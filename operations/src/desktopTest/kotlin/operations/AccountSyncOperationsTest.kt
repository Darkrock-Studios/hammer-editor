package operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountResult
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class AccountSyncOperationsTest {

	private val fileSystem = FakeFileSystem()
	private val json = Json { ignoreUnknownKeys = true }
	private val settings = mockk<GlobalSettingsStore>(relaxed = true)
	private val account = mockk<AccountUseCase>()
	private val sync = mockk<SyncAccountUseCase>()
	private val projects = mockk<ProjectsRepository>(relaxed = true)
	private val metadata = mockk<ProjectMetadataDatasource>()

	private val novel = ProjectDef("Novel", "/projects/Novel".toPath().toHPath())
	private val notes = ProjectDef("Notes", "/projects/Notes".toPath().toHPath())
	private val loggedIn = ServerSettings(url = "hammer.ink", email = "ada@example.com", userId = 7, bearerToken = "t", refreshToken = "r")

	private val registry = OperationRegistry(coreOperations(), object : ProjectResolver {
		override fun resolve(project: String): ProjectDef =
			listOf(novel, notes).firstOrNull { it.name == project } ?: throw OperationException(OperationException.Kind.NotFound, project)

		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	})

	@BeforeEach
	fun setUp() {
		every { settings.serverSettings } returns null
		every { projects.getProjects(any()) } returns listOf(novel, notes)
		every { metadata.readMetadata(novel) } returns ProjectMetadata(Info(created = Instant.DISTANT_PAST, serverProjectId = ProjectId("p1")))
		every { metadata.readMetadata(notes) } returns ProjectMetadata(Info(created = Instant.DISTANT_PAST))
		GlobalContext.startKoin {
			modules(module {
				single { settings }
				single { account }
				single { sync }
				single { projects }
				single { metadata }
				single<FileSystem> { fileSystem }
				single { json }
				single<StrRes> { mockk(relaxed = true) }
			})
		}
	}

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private suspend fun dispatch(name: String, vararg fields: Pair<String, JsonElement>) =
		registry.dispatch(name, buildJsonObject { fields.forEach { (key, value) -> put(key, value) } }).jsonObject

	@Test
	fun `status reports the account without asking the server unless told to`() = runTest {
		every { settings.serverSettings } returns loggedIn
		coEvery { account.testAuth() } returns true

		val status = dispatch("account.status")
		assertEquals(JsonPrimitive(true), status["loggedIn"])
		assertEquals(JsonPrimitive("ada@example.com"), status["email"])
		coVerify(exactly = 0) { account.testAuth() }

		assertEquals(JsonPrimitive(true), dispatch("account.status", "check" to JsonPrimitive(true))["tokenValid"])
	}

	@Test
	fun `login failures are unauthorized, and servers asking for terms are refused`() = runTest {
		coEvery { account.setupServer(any(), any(), any(), false, any(), any()) } returns ServerSetupResult.Failure(null, RuntimeException("Bad password"))
		val login = arrayOf("url" to JsonPrimitive("https://hammer.ink"), "email" to JsonPrimitive("ada@example.com"), "password" to JsonPrimitive("x"))

		val refused = assertFailsWith<OperationException> { dispatch("account.login", *login) }
		assertEquals(OperationException.Kind.Unauthorized, refused.kind)
		assertEquals("Bad password", refused.message)

		coEvery { account.setupServer(any(), any(), any(), false, any(), any()) } returns
			ServerSetupResult.TermsRequired(mockk(relaxed = true))
		assertEquals(OperationException.Kind.InvalidInput, assertFailsWith<OperationException> { dispatch("account.login", *login) }.kind)
		verify { settings.deleteServerSettings() }
	}

	@Test
	fun `login passes the host and scheme separately`() = runTest {
		coEvery { account.setupServer(any(), any(), any(), false, any(), any()) } returns ServerSetupResult.Success

		dispatch("account.login", "url" to JsonPrimitive("http://localhost:8080"), "email" to JsonPrimitive("a@b.c"), "password" to JsonPrimitive("pw"))

		coVerify { account.setupServer("localhost:8080", "a@b.c", "pw", false, null, false) }
	}

	@Test
	fun `login refuses to move to another server without logging out`() = runTest {
		every { settings.serverSettings } returns loggedIn
		val other = arrayOf("url" to JsonPrimitive("https://other.example"), "email" to JsonPrimitive("a@b.c"), "password" to JsonPrimitive("x"))

		assertEquals(OperationException.Kind.InvalidInput, assertFailsWith<OperationException> { dispatch("account.login", *other) }.kind)
		coVerify(exactly = 0) { account.setupServer(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `an unreachable server is a failure, not a rejected login`() = runTest {
		coEvery { account.setupServer(any(), any(), any(), false, any(), any()) } returns
			ServerSetupResult.Failure(null, java.net.ConnectException("Connection refused"))

		assertFailsWith<java.io.IOException> {
			dispatch("account.login", "url" to JsonPrimitive("hammer.ink"), "email" to JsonPrimitive("a@b.c"), "password" to JsonPrimitive("x"))
		}
	}

	@Test
	fun `logout forgets the server and unlinks every project`() = runTest {
		dispatch("account.logout")

		verify { settings.deleteServerSettings() }
		coVerify { projects.removeProjectId(novel) }
		coVerify { projects.removeProjectId(notes) }
	}

	@Test
	fun `sync status reads each project's journal`() = runTest {
		fileSystem.createDirectories("/projects/Novel".toPath())
		val journal = ProjectSynchronizationData(
			lastId = 9,
			newIds = listOf(8, 9),
			lastSync = Instant.fromEpochSeconds(1_700_000_000),
			dirty = listOf(EntityOriginalState(3), EntityOriginalState(8)),
			deletedIds = emptySet(),
		)
		fileSystem.write("/projects/Novel/sync.json".toPath()) {
			writeUtf8(json.encodeToString(ProjectSynchronizationData.serializer(), journal))
		}

		val status = dispatch("sync.status")["projects"]!!.jsonArray.map { it.jsonObject }
		val (notesStatus, novelStatus) = status
		assertEquals(JsonPrimitive(3), novelStatus["pendingChanges"])
		assertEquals(JsonPrimitive(true), novelStatus["linked"])
		assertEquals(JsonPrimitive(false), notesStatus["linked"])
		assertEquals(JsonPrimitive(0), notesStatus["pendingChanges"])
	}

	@Test
	fun `sync needs a login`() = runTest {
		every { settings.serverSettings } returns null
		assertEquals(OperationException.Kind.Unauthorized, assertFailsWith<OperationException> { dispatch("sync.run") }.kind)
	}

	@Test
	fun `sync outcomes set the exit code`() = runTest {
		every { settings.serverSettings } returns loggedIn
		fun syncReturns(vararg outcomes: Pair<String, ProjectSyncOutcome>) {
			coEvery { sync.execute(any(), any()) } returns SyncAccountResult(true, true, mapOf(*outcomes))
		}

		syncReturns("Novel" to ProjectSyncOutcome.Success, "Notes" to ProjectSyncOutcome.Unchanged)
		assertEquals(0, registry.exitCode("sync.run", registry.dispatch("sync.run", buildJsonObject {})))

		syncReturns("Novel" to ProjectSyncOutcome.NeedsResolution, "Notes" to ProjectSyncOutcome.Success)
		val resolution = registry.dispatch("sync.run", buildJsonObject {})
		assertEquals("needs_resolution", resolution.jsonObject["projects"]!!.jsonArray[1].jsonObject["outcome"]!!.jsonPrimitive.content)
		assertEquals(2, registry.exitCode("sync.run", resolution))

		syncReturns("Novel" to ProjectSyncOutcome.NeedsResolution, "Notes" to ProjectSyncOutcome.Failed)
		assertEquals(1, registry.exitCode("sync.run", registry.dispatch("sync.run", buildJsonObject {})))
	}

	@Test
	fun `a sync the server rejects is unauthorized`() = runTest {
		every { settings.serverSettings } returns loggedIn
		coEvery { sync.execute(any(), any()) } coAnswers {
			firstArg<SyncAccountListener>().onUnauthorized()
			SyncAccountResult(false, false, emptyMap())
		}

		assertEquals(OperationException.Kind.Unauthorized, assertFailsWith<OperationException> { dispatch("sync.run") }.kind)
	}

	@Test
	fun `idea conflicts follow the chosen side, and a project filter limits the sync`() = runTest {
		every { settings.serverSettings } returns loggedIn
		val listener = slot<SyncAccountListener>()
		val filter = slot<(com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition) -> Boolean>()
		coEvery { sync.execute(capture(listener), capture(filter)) } returns SyncAccountResult(true, true, emptyMap())
		val local = StoryIdea(IdeaId("i"), Instant.DISTANT_PAST, Instant.DISTANT_PAST, content = "mine")
		val conflict = IdeaConflict(local = local, server = local.copy(content = "theirs"))

		dispatch("sync.run", "onConflict" to JsonPrimitive("server"), "project" to JsonPrimitive("Novel"))
		assertEquals("theirs", listener.captured.onIdeaConflict(conflict)?.content)
		assertEquals(true, filter.captured(com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition(novel, ProjectId("p1"))))
		assertEquals(false, filter.captured(com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition(notes, ProjectId("p2"))))

		dispatch("sync.run")
		assertNull(listener.captured.onIdeaConflict(conflict))
	}
}
