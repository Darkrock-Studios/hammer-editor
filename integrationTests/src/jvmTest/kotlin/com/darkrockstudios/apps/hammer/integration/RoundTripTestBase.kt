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
import io.ktor.client.HttpClient
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

			single<CoroutineContext>(named(DISPATCHER_MAIN)) { mainDispatcher }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
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
}
