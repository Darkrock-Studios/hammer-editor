package synchronizer

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsService
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSessionLock
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.GroupSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.ClientIdeasSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectCreateResolver
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.sync_log_account_project_parked
import com.darkrockstudios.apps.hammer.sync_log_account_project_resumed
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_client_name_taken
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_shared_name_taken
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A co-author's copy of a shared project through whole account sync sessions, on a real projects directory. */
class AccountSyncSharedProjectsTest : ProjectsDirSyncTest() {

	private val sharedId = ProjectId.randomUUID()
	private val alice = ProjectOwner(7L, "Alice")
	private val listing = ApiProjectDefinition("Shared", sharedId, ownerId = alice.id, ownerName = alice.name)
	private val signedIn = ServerSettings(url = "example.test", email = "a@example.test", userId = 1, bearerToken = null, refreshToken = null)
	private val journalJson =
		"""{"lastId":3,"newIds":[3],"lastSync":"2025-01-01T00:00:00Z","dirty":[],"deletedIds":[]}"""

	@BeforeEach
	override fun setup() {
		super.setup()
		coEvery { serverProjectsApi.endProjectsSync(any()) } returns Result.success("ok")
	}

	private fun synchronizer(): ClientAccountSynchronizer {
		val ideas = mockk<ClientIdeasSynchronizer>()
		coEvery { ideas.syncIdeas(any(), any(), any(), any()) } returns true
		val sessionLock = AccountSessionLock()
		return ClientAccountSynchronizer(
			fileSystem = ffs,
			globalSettingsStore = globalSettingsStore,
			projectsRepository = repo,
			serverProjectsApi = serverProjectsApi,
			accountSyncDatasource = accountSyncDatasource,
			groupSyncPhase = GroupSyncPhase(repo, serverProjectsApi, strRes),
			projectAttributeSyncPhase = ProjectAttributeSyncPhase(repo, serverProjectsApi, strRes),
			projectCreateResolver = ProjectCreateResolver(serverProjectsApi, repo, accountSyncDatasource, sessionLock),
			sessionLock = sessionLock,
			ideasSynchronizer = ideas,
			dataMigrator = mockk(relaxed = true),
			networkConnectivity = mockk(relaxed = true),
			json = json,
			toml = toml,
			strRes = strRes,
		)
	}

	private fun serverLists(vararg projects: ApiProjectDefinition) {
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(
			BeginProjectsSyncResponse(syncId = "sync-1", projects = projects.toSet(), deletedProjects = emptySet())
		)
	}

	// The server mock is strict: an unexpected create or delete fails the session.
	private suspend fun sync(): Boolean = synchronizer().syncProjects(onLog = {}, onUnauthorized = {})

	private fun shared(): ProjectDef = assertNotNull(repo.findProject("Shared"))

	private fun journal(def: ProjectDef) = def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME

	/** Shares the project, syncs it down, and gives it a journal with unsynced work. */
	private suspend fun sharedAndSynced(): ProjectDef {
		serverLists(listing)
		assertTrue(sync())
		val def = shared()
		ffs.write(journal(def)) { writeUtf8(journalJson) }
		return def
	}

	@Test
	fun `a shared project is created at the top level with its owner`() = runTest {
		val def = sharedAndSynced()

		assertEquals(sharedId, def.id)
		assertNull(tree().placement[def.id])
		assertEquals(alice, repo.getOwner(def))
		assertEquals(ProjectBaseline("Shared"), accountSyncDatasource.load().projectBaselines[sharedId])
	}

	@Test
	fun `unshared, the project is parked with its folder and journal, and shared again it resumes`() = runTest {
		val def = sharedAndSynced()

		serverLists()
		assertTrue(sync())
		assertTrue(sync())

		assertTrue(ffs.exists(def.path.toOkioPath()))
		assertFalse(repo.isSyncEnabled(def))
		assertEquals(journalJson, ffs.read(journal(def)) { readUtf8() })
		assertFalse(sharedId in accountSyncDatasource.load().projectBaselines)
		assertEquals(1, strRes.calls.count { it.first == Res.string.sync_log_account_project_parked })

		serverLists(listing)
		assertTrue(sync())

		assertTrue(repo.isSyncEnabled(def))
		assertEquals(sharedId, shared().id)
		assertEquals(journalJson, ffs.read(journal(def)) { readUtf8() })
		assertTrue(strRes.calls.any { it.first == Res.string.sync_log_account_project_resumed })
	}

	@Test
	fun `leaving keeps the folder, out of sync, and sends the delete`() = runTest {
		val def = sharedAndSynced()
		coEvery { serverProjectsApi.deleteProject(sharedId, "sync-1") } returns Result.success("ok")

		assertTrue(ProjectsService(repo, synchronizer()).leaveProject(def))
		assertTrue(sync())
		serverLists()
		assertTrue(sync())

		coVerify(exactly = 1) { serverProjectsApi.deleteProject(sharedId, "sync-1") }
		assertTrue(ffs.exists(def.path.toOkioPath()))
		assertFalse(repo.isSyncEnabled(def))
		assertEquals(journalJson, ffs.read(journal(def)) { readUtf8() })
		// Leaving is not news.
		assertFalse(strRes.calls.any { it.first == Res.string.sync_log_account_project_parked })
	}

	@Test
	fun `a project left and then shared again resumes`() = runTest {
		val def = sharedAndSynced()
		coEvery { serverProjectsApi.deleteProject(sharedId, "sync-1") } returns Result.success("ok")
		assertTrue(ProjectsService(repo, synchronizer()).leaveProject(def))
		assertTrue(sync())
		serverLists()
		assertTrue(sync())

		serverLists(listing)
		assertTrue(sync())

		assertTrue(repo.isSyncEnabled(def))
		assertEquals(sharedId, shared().id)
		assertTrue(strRes.calls.any { it.first == Res.string.sync_log_account_project_resumed })
	}

	@Test
	fun `a project marked shared that the server lists as this account's own is unmarked and keeps syncing`() = runTest {
		val def = sharedAndSynced()

		serverLists(ApiProjectDefinition("Shared", sharedId))
		assertTrue(sync())

		assertNull(repo.getOwner(def))
		assertTrue(repo.isSyncEnabled(def))
		assertFalse(ProjectsService(repo, synchronizer()).offersLeave(def))
		assertTrue(sharedId in accountSyncDatasource.load().projectBaselines)
		assertFalse(strRes.calls.any { it.first == Res.string.sync_log_account_project_parked })
	}

	@Test
	fun `an own project the server lists as shared is marked with its owner`() = runTest {
		val local = project("Shared")
		serverLists(ApiProjectDefinition("Shared", local.id, ownerId = alice.id, ownerName = alice.name))

		assertTrue(sync())

		assertEquals(alice, repo.getOwner(local))
		assertTrue(repo.isSyncEnabled(local))
	}

	@Test
	fun `a shared project never takes the folder of a same-named local project`() = runTest {
		val local = project("Shared")
		coEvery { serverProjectsApi.createProject("Shared", local.id, "sync-1", null) } returns
			Result.success(CreateProjectResponse(local.id, alreadyExisted = false))
		serverLists(listing)

		assertTrue(sync())

		assertEquals(listOf(local.id), tree().projects.map { it.id })
		assertNull(repo.getOwner(local))
		assertTrue(strRes.calls.any { it.first == Res.string.sync_log_account_project_create_shared_name_taken })
		assertFalse(strRes.calls.any { it.first == Res.string.sync_log_account_project_create_client_name_taken })
	}

	@Test
	fun `a syncing shared project is not renamed or moved, and a parked one is`() = runTest {
		every { globalSettingsStore.serverSettings } returns signedIn
		val def = sharedAndSynced()
		val service = ProjectsService(repo, synchronizer())

		assertTrue(isFailure(service.renameProject(def, "Mine")))
		assertTrue(isFailure(service.moveProject(def, null)))

		assertTrue(repo.setSyncEnabled(def, false))
		val renamed = service.renameProject(def, "Mine")
		assertTrue(isSuccess(renamed))
		assertEquals("Mine", renamed.data.name)
	}

	@Test
	fun `with no server a shared project is renamed`() = runTest {
		val def = sharedAndSynced()
		every { globalSettingsStore.serverSettings } returns null

		assertTrue(isSuccess(ProjectsService(repo, synchronizer()).renameProject(def, "Mine")))
	}

	@Test
	fun `a parked shared project is deleted here only, even when asked to delete it on the server`() = runTest {
		val def = sharedAndSynced()
		assertTrue(repo.setSyncEnabled(def, false))

		assertTrue(ProjectsService(repo, synchronizer()).deleteProject(def, alsoDeleteOnServer = true))

		assertFalse(ffs.exists(def.path.toOkioPath()))
		assertTrue(accountSyncDatasource.load().projectsToDelete.isEmpty())
	}

	@Test
	fun `a leave the server answers not_member is dropped from the queue`() = runTest {
		val def = sharedAndSynced()
		coEvery { serverProjectsApi.deleteProject(sharedId, "sync-1") } returns Result.failure(
			HttpFailureException(
				statusCode = HttpStatusCode.Forbidden,
				error = HttpResponseError(error = "Forbidden", displayMessage = "nope", errorCode = ApiErrorCode.NOT_MEMBER),
			)
		)
		assertTrue(ProjectsService(repo, synchronizer()).leaveProject(def))

		assertTrue(sync())
		serverLists()
		assertTrue(sync())

		coVerify(exactly = 1) { serverProjectsApi.deleteProject(sharedId, "sync-1") }
		assertTrue(accountSyncDatasource.load().projectsToDelete.isEmpty())
	}
}
