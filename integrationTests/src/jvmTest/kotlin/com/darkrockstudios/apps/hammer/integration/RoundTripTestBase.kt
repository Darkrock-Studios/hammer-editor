package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Base class for round-trip sync integration tests. Spins up a real Jetty server
 * on `127.0.0.1:54321` (via [EndToEndTest]) and a real client Koin context with
 * production wiring.
 *
 * Both server and client share `EndToEndTest`'s [okio.fakefilesystem.FakeFileSystem]
 * for storage. We can't realistically split them — `ServerSecretManager` persists
 * its HMAC secret into the bound `FileSystem`, so any override of that binding
 * regenerates the server secret and breaks token verification. Tests assert
 * against the shared `fileSystem` field rather than real disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class RoundTripTestBase : EndToEndTest(), KoinTest {

	protected val installId: String = "integration-test-install"

	protected lateinit var projectsRoot: Path

	protected lateinit var account: TestAccount
	protected lateinit var authToken: Token
	protected val userId: Long = 1

	private var mainExecutor: java.util.concurrent.ExecutorService? = null
	private val loadedClientModules = mutableListOf<Module>()
	private val openClients = mutableListOf<HeadlessClient>()

	@BeforeEach
	override fun setup() {
		super.setup()

		// Route client (Napier) logs to stdout so test failures show what the
		// sync pipeline actually did.
		Napier.takeLogarithm()
		Napier.base(DebugAntilog())

		// FakeFileSystem won't auto-create parent dirs on first write the way
		// FileSystem.SYSTEM does on a JUnit @TempDir — materialize the path first.
		projectsRoot = "/client/projects".toPath()
		fileSystem.createDirectories(projectsRoot)

		// Match the existing e2e tests' fixture: whitelist disabled, so any
		// authenticated user can sync (instead of being rejected with 401).
		database().execute(
			"INSERT INTO server_config VALUES ('whitelist_enabled', 'false', to_timestamp(1704067200));"
		)

		doStartServer()

		account = TestAccount(email = "test@test.com", password = "password123!@#")
		E2eTestData.createAccount(account, database())
		authToken = E2eTestData.createAuthToken(
			userId = userId,
			installId = installId,
			database = database(),
			tokenHasher = tokenHasher(),
		)

		// Dispatchers.Main isn't available in plain JVM tests; install a single-thread
		// dispatcher under that name so `withContext(Main)` works.
		val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "RoundTrip-Main") }
		mainExecutor = executor
		val mainDispatcher = executor.asCoroutineDispatcher()
		Dispatchers.setMain(mainDispatcher)

		startClientKoin(mainDispatcher)
	}

	@AfterEach
	fun tearDownClient() {
		// Close tracked clients before the modules unload, so a test that throws before its own
		// cleanup can't leak its project scope into the next test.
		openClients.forEach { runCatching { it.close() } }
		openClients.clear()
		try {
			GlobalContext.getOrNull()?.let {
				it.get<HttpClient>().close()
			}
		} catch (e: Throwable) {
			// HttpClient.close() failing here leaks the Ktor engine's executor
			// threads into subsequent tests — surface it instead of swallowing.
			Napier.w("Failed to close HttpClient during test teardown", e)
		}
		// Unload only the modules we added — server's modules stay so EndToEndTest's
		// tearDown can still stop the server cleanly via its own Koin.
		if (loadedClientModules.isNotEmpty()) {
			unloadKoinModules(loadedClientModules.toList())
			loadedClientModules.clear()
		}
		Dispatchers.resetMain()
		mainExecutor?.shutdownNow()
		mainExecutor = null
	}

	/**
	 * Create a project record on the server side (database insert only). Returns
	 * the [Uuid] the server uses as the project's identity.
	 */
	protected fun seedServerProject(name: String): Uuid {
		val uuid = Uuid.random()
		E2eTestData.createProject(
			project = com.darkrockstudios.apps.hammer.e2e.util.TestProject(
				name = name,
				uuid = uuid,
				userId = userId,
			),
			database = database(),
		)
		return uuid
	}

	/** Returns the server's auto-assigned numeric id for a project, or null. */
	protected fun serverNumericProjectIdFor(name: String): Long? {
		return database().serverDatabase.projectQueries
			.findProjectByName(userId, name)
			.executeAsOneOrNull()
			?.id
	}

	/** Pre-seed an entity on the server side, tied to a server-assigned project id. */
	protected fun seedServerEntity(serverNumericProjectId: Long, entity: ApiProjectEntity) {
		E2eTestData.insertEntity(
			userId = userId,
			projectId = serverNumericProjectId,
			entity = entity,
			testDatabase = database(),
			contentEncryptor = encryptor() as ContentEncryptor,
		)
	}

	/** Mark a server-side entity as deleted (server's deleted-entity tombstone). */
	protected fun seedServerEntityDeletion(serverNumericProjectId: Long, entityId: Long) {
		E2eTestData.insertDeletedEntity(
			id = entityId,
			userId = userId,
			projectId = serverNumericProjectId,
			testDatabase = database(),
		)
	}

	/**
	 * Replace an existing server-side entity with a new version, simulating
	 * another device having synced its edit before the client.
	 */
	protected fun mutateServerEntity(serverNumericProjectId: Long, entity: ApiProjectEntity) {
		database().serverDatabase.storyEntityQueries.deleteEntity(
			userId = userId,
			projectId = serverNumericProjectId,
			id = entity.id.toLong(),
		)
		seedServerEntity(serverNumericProjectId, entity)
	}

	private fun startClientKoin(mainDispatcher: CoroutineDispatcher) {
		val initialGlobalSettings = GlobalSettings(
			projectsDirectory = projectsRoot.toString(),
			installId = installId,
			// Sync's BackupOperation tries to zip the project dir; zipping on a
			// FakeFileSystem path doesn't work cleanly. The backup feature isn't
			// what these tests are validating.
			automaticBackups = false,
		)

		val sharedFileSystem = fileSystem  // EndToEndTest's FakeFileSystem
		val testOverrides = module {
			// In-memory settings datasources prevent the production filesystem ones from
			// being instantiated, which would otherwise write to %APPDATA% via AppDirs.
			single<GlobalSettingsDatasource> { InMemoryGlobalSettingsDatasource(initialGlobalSettings) }
			single<ServerSettingsDatasource> { InMemoryServerSettingsDatasource() }

			// Compose Resources isn't initialized in plain JVM tests; stub StrRes so any
			// sync log path that interpolates a string doesn't blow up.
			single<StrRes> { testStrRes() }

			// Keep FileSystem pinned to the shared FakeFileSystem. The client's mainModule
			// would otherwise rebind it to FileSystem.SYSTEM via getPlatformFilesystem(),
			// which would break the server's ServerSecretManager (it reloads its HMAC
			// secret from disk; if the binding changes mid-test it generates a new one
			// and rejects the pre-seeded auth token).
			single<FileSystem> { sharedFileSystem }

			// FakeFileSystem is not thread-safe, so bind IO and Default to the same single-threaded
			// dispatcher as Main: this serializes all client-side filesystem work and keeps
			// concurrent opens from corrupting its open-file list.
			single<CoroutineContext>(named(DISPATCHER_MAIN)) { mainDispatcher }
			single<CoroutineContext>(named(DISPATCHER_IO)) { mainDispatcher }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { mainDispatcher }
		}

		val clientModules = listOf(mainModule, testOverrides)
		loadKoinModules(clientModules)
		loadedClientModules.addAll(clientModules)
	}

	/**
	 * Build the client-side [ServerSettings] pointing at the in-process Jetty
	 * server, with this test's auth token preloaded.
	 */
	protected fun makeServerSettings(): ServerSettings = ServerSettings(
		ssl = false,
		url = "127.0.0.1:$TEST_PORT",
		email = account.email,
		userId = userId,
		bearerToken = authToken.auth,
		refreshToken = authToken.refresh,
	)

	/** Creates a client and registers it for teardown-close so it can't leak the project scope. */
	protected suspend fun newClient(projectName: String): HeadlessClient =
		HeadlessClient.create(projectName, makeServerSettings()).also { openClients += it }

	/**
	 * Opens a second device on the same server project as [primary], which must have synced at least
	 * once so its server project id exists. The second device gets its own local project dir
	 * ([localName], which must differ from the first device's name) but binds to the same server
	 * project — the realistic "two devices, one account, one project" setup.
	 */
	protected suspend fun secondDeviceFor(primary: HeadlessClient, localName: String): HeadlessClient {
		val sharedId = get<ProjectMetadataDatasource>().loadProjectId(primary.projectDef)
			?: error("primary device has no server project id yet — sync it before opening a second device")
		return HeadlessClient.create(localName, makeServerSettings(), serverProjectId = sharedId)
			.also { openClients += it }
	}

	/** The hash the server currently stores for an entity, or null if it holds none. */
	protected fun serverEntityHash(projectName: String, entityId: Int): String? {
		val projectId = serverNumericProjectIdFor(projectName) ?: return null
		return database().serverDatabase.storyEntityQueries
			.getEntityHash(userId = userId, projectId = projectId, id = entityId.toLong())
			.executeAsOneOrNull()
	}

	/** Runs a sync that must not hit the conflict resolver; returns the sync's success flag. */
	protected suspend fun HeadlessClient.syncNoConflict(): Boolean {
		var conflicted = false
		val ok = sync(resolveConflict = { entity -> conflicted = true; entity })
		assertFalse(conflicted, "single-client resync raised a phantom conflict")
		return ok
	}

	/** The live entity id→hash map the server currently holds for a project. */
	protected fun serverEntityHashes(projectName: String): Map<Int, String> {
		val projectId = serverNumericProjectIdFor(projectName) ?: return emptyMap()
		return database().serverDatabase.storyEntityQueries
			.getEntityHashes(userId = userId, projectId = projectId)
			.executeAsList()
			.associate { it.id.toInt() to it.hash }
	}

	/** The live entity id→hash map a client computes from its own local state. */
	protected suspend fun clientEntityHashes(client: HeadlessClient): Map<Int, String> {
		val synchronizers: EntitySynchronizers = client.scope.get()
		return synchronizers.synchronizers.values
			.flatMap { it.hashEntities(emptyList()) }
			.associate { it.id to it.hash }
	}

	/**
	 * Convergence oracle: every client holds exactly the entity set the server holds, hash for hash.
	 * Model-free — it doesn't care what the entities *should* be, only that client and server agree,
	 * which is the property every sync must establish.
	 */
	protected suspend fun assertConverged(projectName: String, vararg clients: HeadlessClient) {
		val server = serverEntityHashes(projectName)
		clients.forEachIndexed { index, client ->
			assertEquals(
				server,
				clientEntityHashes(client),
				"client #$index did not converge with the server",
			)
		}
	}

	/**
	 * Stability oracle: an immediate extra sync moves nothing over the wire. Any client/server hash
	 * divergence shows up here as a re-download or re-upload, so this catches drift even when the two
	 * sides happen to be equally wrong.
	 */
	protected suspend fun assertResyncSilent(client: HeadlessClient) {
		val wire = tapWire()
		assertTrue(client.syncNoConflict(), "resync should succeed")
		assertEquals(emptyList(), wire.entitiesPulled(), "resync re-downloaded an entity")
		assertEquals(emptyList(), wire.entitiesUploaded(), "resync re-uploaded an entity")
	}

	/** One HTTP request the client made, captured by [tapWire]. */
	protected data class WireCall(val method: String, val path: String, val status: Int)

	/** Records the HTTP calls the client makes so a test can assert what actually crossed the wire. */
	protected class WireTap {
		private val lock = Any()
		private val recorded = mutableListOf<WireCall>()

		fun record(call: WireCall) = synchronized(lock) { recorded += call }
		fun reset() = synchronized(lock) { recorded.clear() }
		val calls: List<WireCall> get() = synchronized(lock) { recorded.toList() }

		/** IDs whose entity body the server actually sent down (download_entity → 200). */
		fun entitiesPulled(): List<Int> = calls
			.filter { it.path.contains("/download_entity/") && it.status == 200 }
			.mapNotNull { idFromPath(it.path) }

		/** IDs the client pushed up (upload_entity, any accepted status). */
		fun entitiesUploaded(): List<Int> = calls
			.filter { it.path.contains("/upload_entity/") && it.status in 200..299 }
			.mapNotNull { idFromPath(it.path) }

		// Paths can carry a query string; the id is the last path segment before any '?'.
		private fun idFromPath(path: String): Int? =
			path.substringBefore('?').substringAfterLast('/').toIntOrNull()
	}

	/**
	 * Installs an [HttpSend] interceptor on the client's shared [HttpClient] that records every
	 * request and its response status. The interceptor runs inline on the send pipeline, so what it
	 * captures is exactly what went over the wire — a 200 on `download_entity` is a real pull, a 304
	 * is the server saying "you already have this".
	 */
	protected fun tapWire(): WireTap {
		val tap = WireTap()
		get<HttpClient>().plugin(HttpSend).intercept { request ->
			val call = execute(request)
			tap.record(WireCall(request.method.value, request.url.buildString(), call.response.status.value))
			call
		}
		return tap
	}
}
