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
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncToggle
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.GroupSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.ClientIdeasSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectCreateResolver
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.sync_log_account_project_parked
import com.darkrockstudios.apps.hammer.sync_log_account_project_resumed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A co-author's copy of a shared project through whole account sync sessions, on a real projects directory. */
class AccountSyncSharedProjectsTest : ProjectsDirSyncTest() {

	private val sharedId = ProjectId.randomUUID()
	private val alice = ProjectOwner(7L, "Alice")
	private val listing = ApiProjectDefinition("Shared", sharedId, ownerId = alice.id, ownerName = alice.name)
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
		assertTrue(repo.isParked(def))
		assertEquals(journalJson, ffs.read(journal(def)) { readUtf8() })
		assertFalse(sharedId in accountSyncDatasource.load().projectBaselines)
		assertEquals(1, strRes.calls.count { it.first == Res.string.sync_log_account_project_parked })

		serverLists(listing)
		assertTrue(sync())

		assertTrue(repo.isSyncEnabled(def))
		assertFalse(repo.isParked(def))
		assertEquals(sharedId, shared().id)
		val resumed = json.decodeFromString<ProjectSynchronizationData>(ffs.read(journal(def)) { readUtf8() })
		assertTrue(resumed.verifyAllEntities)
		assertEquals(listOf(3), resumed.newIds)
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
	fun `a parked project uploaded as one's own is created under a fresh id`() = runTest {
		sharedAndSynced()
		serverLists()
		assertTrue(sync())

		val toggle = ProjectSyncToggle(repo, accountSyncDatasource)
		assertEquals(ProjectSyncToggle.Result.NeedsConfirmation, toggle.turnOn(shared()))
		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOn(shared(), uploadAsOwn = true))
		val own = shared()
		assertNotEquals(sharedId, own.id)
		assertNull(repo.getOwner(own))
		coEvery { serverProjectsApi.createProject("Shared", own.id, "sync-1", null) } returns
			Result.success(CreateProjectResponse(own.id, alreadyExisted = false))

		assertTrue(sync())

		coVerify(exactly = 1) { serverProjectsApi.createProject("Shared", own.id, "sync-1", null) }
		assertEquals(ProjectBaseline("Shared"), accountSyncDatasource.load().projectBaselines[own.id])
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
	}
}
