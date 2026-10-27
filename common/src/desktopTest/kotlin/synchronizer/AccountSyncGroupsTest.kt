package synchronizer

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSessionLock
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.GroupSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.ClientIdeasSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectCreateResolver
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.sync_log_account_project_in_use
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testGroupId
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Groups and project placement through the whole account sync session. */
class AccountSyncGroupsTest : ProjectsDirSyncTest() {

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

	private fun serverState(
		projects: Set<ApiProjectDefinition> = emptySet(),
		groups: Set<ApiGroupDefinition> = emptySet(),
	) {
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(
			BeginProjectsSyncResponse(
				syncId = "sync-1",
				projects = projects,
				deletedProjects = emptySet(),
				groups = groups,
			)
		)
	}

	private suspend fun stored(data: ProjectsSynchronizationData) = accountSyncDatasource.save(data)

	private suspend fun sync(): Boolean = synchronizer().syncProjects(onLog = {}, onUnauthorized = {})

	private suspend fun baselines() = accountSyncDatasource.load()

	private fun acceptCreate(id: ProjectId, groupId: GroupId? = null) {
		coEvery { serverProjectsApi.createProject(any(), id, "sync-1", groupId) } returns
			Result.success(CreateProjectResponse(id, alreadyExisted = false, groupId = groupId))
	}

	@Test
	fun `the session runs deletes, groups, renames, moves, creates, then group deletes`() = runTest {
		val doomed = ProjectId("doomed")
		val goneGroup = ApiGroupDefinition(GroupId("gone"), "Gone")
		val newGroup = group("Shelf")
		val renamed = project("Renamed")
		val moved = project("Moved", newGroup)
		val fresh = project("Fresh")
		stored(
			ProjectsSynchronizationData(
				projectsToDelete = setOf(doomed),
				groupBaselines = mapOf(goneGroup.uuid to GroupBaseline("Gone")),
				projectBaselines = mapOf(
					renamed.id to ProjectBaseline("Original"),
					moved.id to ProjectBaseline("Moved"),
				),
			)
		)
		serverState(
			projects = setOf(
				ApiProjectDefinition("Doomed", doomed),
				ApiProjectDefinition("Original", renamed.id),
				ApiProjectDefinition("Moved", moved.id),
			),
			groups = setOf(goneGroup),
		)
		coEvery { serverProjectsApi.deleteProject(doomed, "sync-1") } returns Result.success("ok")
		acceptUpserts()
		coEvery { serverProjectsApi.renameProject(renamed.id, "sync-1", "Renamed") } returns Result.success("ok")
		coEvery { serverProjectsApi.moveProject(moved.id, testGroupId("Shelf"), "sync-1") } returns Result.success("ok")
		acceptCreate(fresh.id)
		coEvery { serverProjectsApi.deleteGroup(goneGroup.uuid, "sync-1") } returns Result.success("ok")

		assertTrue(sync())

		coVerifyOrder {
			serverProjectsApi.deleteProject(doomed, "sync-1")
			serverProjectsApi.upsertGroup(ApiGroupDefinition(testGroupId("Shelf"), "Shelf"), "sync-1")
			serverProjectsApi.renameProject(renamed.id, "sync-1", "Renamed")
			serverProjectsApi.moveProject(moved.id, testGroupId("Shelf"), "sync-1")
			serverProjectsApi.createProject("Fresh", fresh.id, "sync-1", null)
			serverProjectsApi.deleteGroup(goneGroup.uuid, "sync-1")
			serverProjectsApi.endProjectsSync("sync-1")
		}
		val after = baselines()
		assertEquals(ProjectBaseline("Renamed"), after.projectBaselines[renamed.id])
		assertEquals(ProjectBaseline("Moved", testGroupId("Shelf")), after.projectBaselines[moved.id])
		assertEquals(ProjectBaseline("Fresh"), after.projectBaselines[fresh.id])
		assertEquals(mapOf(testGroupId("Shelf") to GroupBaseline("Shelf")), after.groupBaselines)
	}

	@Test
	fun `a project renamed on both sides takes the server's name`() = runTest {
		val def = project("Mine")
		stored(ProjectsSynchronizationData(projectBaselines = mapOf(def.id to ProjectBaseline("Original"))))
		serverState(projects = setOf(ApiProjectDefinition("Theirs", def.id)))

		sync()

		assertEquals("Theirs", repo.findProject(def.id)?.name)
		assertEquals(ProjectBaseline("Theirs"), baselines().projectBaselines[def.id])
	}

	@Test
	fun `a project in use keeps its baseline when the server renamed it`() = runTest {
		val def = project("Mine")
		stored(ProjectsSynchronizationData(projectBaselines = mapOf(def.id to ProjectBaseline("Mine"))))
		serverState(projects = setOf(ApiProjectDefinition("Theirs", def.id)))
		val scope = createProjectScope(def)

		try {
			sync()
		} finally {
			scope.close()
		}

		assertEquals("Mine", repo.findProject(def.id)?.name)
		assertEquals(ProjectBaseline("Mine"), baselines().projectBaselines[def.id])
		assertEquals(listOf(listOf<Any>("Mine")), strRes.argsOf(Res.string.sync_log_account_project_in_use))
	}

	@Test
	fun `an in-use project renamed and moved on the server logs in-use once and is not moved after the rename fails`() = runTest {
		val shelf = group("Shelf")
		val def = project("Mine")
		stored(
			ProjectsSynchronizationData(
				groupBaselines = mapOf(testGroupId("Shelf") to GroupBaseline("Shelf")),
				projectBaselines = mapOf(def.id to ProjectBaseline("Mine")),
			)
		)
		serverState(
			projects = setOf(ApiProjectDefinition("Theirs", def.id, testGroupId("Shelf"))),
			groups = setOf(ApiGroupDefinition(testGroupId("Shelf"), "Shelf")),
		)
		val scope = createProjectScope(def)

		try {
			sync()
		} finally {
			scope.close()
		}

		assertEquals("Mine", repo.findProject(def.id)?.name)
		assertNull(tree().placement[def.id])
		assertEquals(ProjectBaseline("Mine"), baselines().projectBaselines[def.id])
		// The failed rename skips the move, so the in-use message is logged only once.
		assertEquals(listOf(listOf<Any>("Mine")), strRes.argsOf(Res.string.sync_log_account_project_in_use))
		assertFalse(ffs.exists(shelf / "Mine"))
	}

	@Test
	fun `a project moved on the server moves into its group locally`() = runTest {
		val shelf = group("Shelf")
		val def = project("Book")
		stored(
			ProjectsSynchronizationData(
				groupBaselines = mapOf(testGroupId("Shelf") to GroupBaseline("Shelf")),
				projectBaselines = mapOf(def.id to ProjectBaseline("Book")),
			)
		)
		serverState(
			projects = setOf(ApiProjectDefinition("Book", def.id, testGroupId("Shelf"))),
			groups = setOf(ApiGroupDefinition(testGroupId("Shelf"), "Shelf")),
		)

		sync()

		assertTrue(ffs.exists(shelf / "Book"))
		assertEquals(ProjectBaseline("Book", testGroupId("Shelf")), baselines().projectBaselines[def.id])
	}

	@Test
	fun `a project dragged into a group outside the app moves on the server`() = runTest {
		val shelf = group("Shelf")
		val def = project("Book", shelf)
		stored(
			ProjectsSynchronizationData(
				groupBaselines = mapOf(testGroupId("Shelf") to GroupBaseline("Shelf")),
				projectBaselines = mapOf(def.id to ProjectBaseline("Book")),
			)
		)
		serverState(
			projects = setOf(ApiProjectDefinition("Book", def.id)),
			groups = setOf(ApiGroupDefinition(testGroupId("Shelf"), "Shelf")),
		)
		coEvery { serverProjectsApi.moveProject(def.id, testGroupId("Shelf"), "sync-1") } returns Result.success("ok")

		sync()

		coVerify { serverProjectsApi.moveProject(def.id, testGroupId("Shelf"), "sync-1") }
		assertEquals(ProjectBaseline("Book", testGroupId("Shelf")), baselines().projectBaselines[def.id])
	}

	@Test
	fun `a project in a group that failed to reach the server is neither moved nor settled`() = runTest {
		val def = project("Book", group("Shelf"))
		stored(ProjectsSynchronizationData(projectBaselines = mapOf(def.id to ProjectBaseline("Book"))))
		serverState(projects = setOf(ApiProjectDefinition("Book", def.id)))
		coEvery { serverProjectsApi.upsertGroup(any(), any()) } returns Result.failure(serverError())

		sync()

		coVerify(exactly = 0) { serverProjectsApi.moveProject(any(), any(), any()) }
		assertEquals(ProjectBaseline("Book"), baselines().projectBaselines[def.id])
	}

	@Test
	fun `a server project is created inside its group`() = runTest {
		val shelf = group("Shelf")
		val id = testProjectId("Book")
		serverState(
			projects = setOf(ApiProjectDefinition("Book", id, testGroupId("Shelf"))),
			groups = setOf(ApiGroupDefinition(testGroupId("Shelf"), "Shelf")),
		)

		sync()

		assertTrue(ffs.exists(shelf / "Book"))
		assertEquals(testGroupId("Shelf"), tree().placement[id])
		assertEquals(ProjectBaseline("Book", testGroupId("Shelf")), baselines().projectBaselines[id])
	}

	@Test
	fun `a server project whose group is missing here lands in the nearest ancestor, unsettled`() = runTest {
		val top = group("Top")
		val id = testProjectId("Book")
		// Deleted here since it last synced, so it is not recreated locally.
		val deletedHere = ApiGroupDefinition(GroupId("deleted-here"), "Deleted", testGroupId("Top"))
		stored(ProjectsSynchronizationData(groupBaselines = mapOf(deletedHere.uuid to GroupBaseline("Deleted", testGroupId("Top")))))
		serverState(
			projects = setOf(ApiProjectDefinition("Book", id, deletedHere.uuid)),
			groups = setOf(ApiGroupDefinition(testGroupId("Top"), "Top"), deletedHere),
		)
		coEvery { serverProjectsApi.deleteGroup(deletedHere.uuid, "sync-1") } returns Result.success("ok")

		sync()

		assertTrue(ffs.exists(top / "Book"))
		assertNull(baselines().projectBaselines[id])
	}

	@Test
	fun `a local project in a server group is created there and the resolver records its placement`() = runTest {
		val def = project("Book", group("Shelf"))
		stored(ProjectsSynchronizationData(groupBaselines = mapOf(testGroupId("Shelf") to GroupBaseline("Shelf"))))
		serverState(groups = setOf(ApiGroupDefinition(testGroupId("Shelf"), "Shelf")))
		acceptCreate(def.id, testGroupId("Shelf"))

		sync()

		coVerify { serverProjectsApi.createProject("Book", def.id, "sync-1", testGroupId("Shelf")) }
		assertEquals(ProjectBaseline("Book", testGroupId("Shelf")), baselines().projectBaselines[def.id])
	}

	@Test
	fun `a group deleted here moves its projects on the server before the group goes`() = runTest {
		val def = project("Book")
		val gone = ApiGroupDefinition(GroupId("gone"), "Gone")
		stored(
			ProjectsSynchronizationData(
				groupBaselines = mapOf(gone.uuid to GroupBaseline("Gone")),
				projectBaselines = mapOf(def.id to ProjectBaseline("Book", gone.uuid)),
			)
		)
		serverState(projects = setOf(ApiProjectDefinition("Book", def.id, gone.uuid)), groups = setOf(gone))
		coEvery { serverProjectsApi.moveProject(def.id, null, "sync-1") } returns Result.success("ok")
		coEvery { serverProjectsApi.deleteGroup(gone.uuid, "sync-1") } returns Result.success("ok")

		sync()

		coVerifyOrder {
			serverProjectsApi.moveProject(def.id, null, "sync-1")
			serverProjectsApi.deleteGroup(gone.uuid, "sync-1")
		}
		val after = baselines()
		assertTrue(after.groupBaselines.isEmpty())
		assertEquals(ProjectBaseline("Book"), after.projectBaselines[def.id])
	}

	@Test
	fun `the final write keeps a failed item's old baseline, updates agreed ones and drops retired ones`() = runTest {
		val failed = project("Failed")
		val agreed = project("Agreed")
		val tombstoned = ProjectId("tombstoned")
		stored(
			ProjectsSynchronizationData(
				projectBaselines = mapOf(
					failed.id to ProjectBaseline("Failed"),
					agreed.id to ProjectBaseline("Old"),
					tombstoned to ProjectBaseline("Tombstoned"),
				),
			)
		)
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(
			BeginProjectsSyncResponse(
				syncId = "sync-1",
				projects = setOf(
					ApiProjectDefinition("Failed Server", failed.id),
					ApiProjectDefinition("Agreed", agreed.id),
				),
				deletedProjects = setOf(tombstoned),
			)
		)
		ffs.createDirectories(root / "Failed Server")

		sync()

		assertEquals(
			mapOf(failed.id to ProjectBaseline("Failed"), agreed.id to ProjectBaseline("Agreed")),
			baselines().projectBaselines,
		)
	}

	@Test
	fun `a corrupt sync file starts over and the sync still succeeds`() = runTest {
		val path = accountSyncDatasource.getSyncDataPath().toOkioPath()
		ffs.write(path) { writeUtf8("not json") }
		serverState()

		assertTrue(sync())

		assertEquals(ProjectsSynchronizationData(), baselines())
	}
}
