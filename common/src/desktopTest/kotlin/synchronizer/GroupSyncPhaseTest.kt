package synchronizer

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.UpsertGroupResponse
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.BaselineLedger
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.GroupSyncPhase
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.PendingGroupDeletes
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import com.darkrockstudios.apps.hammer.sync_log_account_group_adopt_server
import com.darkrockstudios.apps.hammer.sync_log_account_group_id_reminted
import com.darkrockstudios.apps.hammer.sync_log_account_group_in_use
import com.darkrockstudios.apps.hammer.sync_log_account_group_name_taken_client
import com.darkrockstudios.apps.hammer.sync_log_account_group_skipped_parent_failed
import com.darkrockstudios.apps.hammer.sync_log_account_group_update_server_cycle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testGroupId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupSyncPhaseTest : ProjectsDirSyncTest() {

	private val ledger = BaselineLedger()

	private fun phase() = GroupSyncPhase(repo, serverProjectsApi, strRes)

	private suspend fun reconcile(
		server: Set<ApiGroupDefinition>,
		baselines: Map<GroupId, GroupBaseline> = emptyMap(),
	): PendingGroupDeletes = phase().reconcile("sync-1", server, baselines, ledger, onLog = {}, onUnauthorized = {})

	private fun storedBaselines(before: Map<GroupId, GroupBaseline> = emptyMap()) =
		ledger.applyTo(ProjectsSynchronizationData(groupBaselines = before)).groupBaselines

	private fun server(name: String, parent: GroupId? = null, id: GroupId = testGroupId(name), description: String? = null) =
		ApiGroupDefinition(id, name, parent, description)

	@Test
	fun `a nested local chain is created on the server parents first`() = runTest {
		val a = group("A")
		val b = group("B", a)
		group("C", b)
		acceptUpserts()

		reconcile(emptySet())

		coVerifyOrder {
			serverProjectsApi.upsertGroup(server("A"), "sync-1")
			serverProjectsApi.upsertGroup(server("B", testGroupId("A")), "sync-1")
			serverProjectsApi.upsertGroup(server("C", testGroupId("B")), "sync-1")
		}
		assertEquals(setOf(testGroupId("A"), testGroupId("B"), testGroupId("C")), ledger.confirmedGroupIds)
		assertEquals(GroupBaseline("C", testGroupId("B")), storedBaselines()[testGroupId("C")])
	}

	@Test
	fun `a subgroup is skipped when its parent fails to reach the server`() = runTest {
		group("B", group("A"))
		coEvery { serverProjectsApi.upsertGroup(server("A"), "sync-1") } returns Result.failure(serverError())

		reconcile(emptySet())

		coVerify(exactly = 1) { serverProjectsApi.upsertGroup(any(), any()) }
		assertEquals(listOf(listOf<Any>("B")), strRes.argsOf(Res.string.sync_log_account_group_skipped_parent_failed))
		assertTrue(storedBaselines().isEmpty())
	}

	@Test
	fun `new local groups adopt same-named server groups level by level`() = runTest {
		val trilogy = group("Trilogy", id = GroupId("local-1"))
		group("Book One", trilogy, id = GroupId("local-2"))
		val serverTrilogy = server("Trilogy", id = GroupId("server-1"))
		val serverBook = server("Book One", serverTrilogy.uuid, id = GroupId("server-2"))

		reconcile(setOf(serverTrilogy, serverBook))

		assertEquals(setOf(GroupId("server-1"), GroupId("server-2")), tree().groups.map { it.id }.toSet())
		coVerify(exactly = 0) { serverProjectsApi.upsertGroup(any(), any()) }
		assertEquals(2, strRes.argsOf(Res.string.sync_log_account_group_adopt_server).size)
		assertEquals(GroupBaseline("Book One", GroupId("server-1")), storedBaselines()[GroupId("server-2")])
	}

	@Test
	fun `the server matching a new group by name hands over its id`() = runTest {
		val notes = group("notes", id = GroupId("local-1"))
		group("Inner", notes)
		acceptUpserts()
		coEvery { serverProjectsApi.upsertGroup(server("notes", id = GroupId("local-1")), "sync-1") } returns
			Result.success(UpsertGroupResponse(GroupId("server-1"), alreadyExisted = true))

		reconcile(emptySet())

		assertEquals(GroupId("server-1"), groupNamed("notes").id)
		coVerify { serverProjectsApi.upsertGroup(server("Inner", GroupId("server-1")), "sync-1") }
		assertTrue(GroupId("server-1") in ledger.confirmedGroupIds)
	}

	@Test
	fun `an id another account holds is re-minted once`() = runTest {
		group("Mine", id = GroupId("taken"))
		coEvery { serverProjectsApi.upsertGroup(any(), any()) } answers {
			val sent = firstArg<ApiGroupDefinition>()
			if (sent.uuid == GroupId("taken")) {
				Result.failure(conflict(ApiErrorCode.GROUP_ID_TAKEN))
			} else {
				Result.success(UpsertGroupResponse(sent.uuid, alreadyExisted = false))
			}
		}

		reconcile(emptySet())

		val reminted = groupNamed("Mine").id
		assertTrue(reminted != GroupId("taken"))
		coVerify(exactly = 2) { serverProjectsApi.upsertGroup(any(), any()) }
		assertEquals(listOf(listOf<Any>("Mine")), strRes.argsOf(Res.string.sync_log_account_group_id_reminted))
		assertEquals(GroupBaseline("Mine"), storedBaselines()[reminted])
	}

	@Test
	fun `server groups are created locally top down under their server ids`() = runTest {
		val a = server("A")
		val b = server("B", a.uuid, description = "Bee")

		reconcile(setOf(b, a))

		val created = tree()
		assertEquals(a.uuid, created.group(b.uuid)?.parentId)
		assertEquals("Bee", created.group(b.uuid)?.description)
		assertEquals(GroupBaseline("B", a.uuid, "Bee"), storedBaselines()[b.uuid])
	}

	@Test
	fun `a server group whose name is taken locally gets a suffix that is pushed back`() = runTest {
		project("Notes")
		val notes = server("Notes")
		acceptUpserts()

		reconcile(setOf(notes))

		assertEquals("Notes (2)", tree().group(notes.uuid)?.name)
		coVerify { serverProjectsApi.upsertGroup(server("Notes (2)", id = notes.uuid), "sync-1") }
		assertEquals(listOf(listOf<Any>("Notes", "Notes (2)")), strRes.argsOf(Res.string.sync_log_account_group_name_taken_client))
		assertEquals(GroupBaseline("Notes (2)"), storedBaselines()[notes.uuid])
	}

	@Test
	fun `a group moved outside the app is pushed`() = runTest {
		group("B", group("A"))
		acceptUpserts()
		val baselines = mapOf(testGroupId("A") to GroupBaseline("A"), testGroupId("B") to GroupBaseline("B"))

		reconcile(setOf(server("A"), server("B")), baselines)

		coVerify { serverProjectsApi.upsertGroup(server("B", testGroupId("A")), "sync-1") }
		assertEquals(GroupBaseline("B", testGroupId("A")), storedBaselines(baselines)[testGroupId("B")])
	}

	@Test
	fun `a group moved on the server is moved locally`() = runTest {
		group("A")
		group("B")
		val baselines = mapOf(testGroupId("A") to GroupBaseline("A"), testGroupId("B") to GroupBaseline("B"))

		reconcile(setOf(server("A"), server("B", testGroupId("A"))), baselines)

		assertEquals(testGroupId("A"), groupNamed("B").parentId)
		assertTrue(ffs.exists(root / "A" / "B"))
	}

	@Test
	fun `a group renamed on both sides takes the server's name`() = runTest {
		group("Mine", id = testGroupId("G"))
		val baselines = mapOf(testGroupId("G") to GroupBaseline("Original"))

		reconcile(setOf(server("Theirs", id = testGroupId("G"))), baselines)

		assertEquals("Theirs", tree().group(testGroupId("G"))?.name)
		coVerify(exactly = 0) { serverProjectsApi.upsertGroup(any(), any()) }
	}

	@Test
	fun `a move the server refuses as a cycle keeps the old baseline`() = runTest {
		group("B", group("A"))
		val baselines = mapOf(testGroupId("A") to GroupBaseline("A"), testGroupId("B") to GroupBaseline("B"))
		coEvery { serverProjectsApi.upsertGroup(any(), any()) } returns Result.failure(conflict(ApiErrorCode.GROUP_CYCLE))

		reconcile(setOf(server("A"), server("B")), baselines)

		assertEquals(GroupBaseline("B"), storedBaselines(baselines)[testGroupId("B")])
		assertEquals(listOf(listOf<Any>("B")), strRes.argsOf(Res.string.sync_log_account_group_update_server_cycle))
	}

	@Test
	fun `a group holding a project in use is left alone`() = runTest {
		val dir = group("Old", id = testGroupId("G"))
		val open = project("Book", dir)
		val baselines = mapOf(testGroupId("G") to GroupBaseline("Old"))
		val scope = createProjectScope(open)

		try {
			reconcile(setOf(server("New", id = testGroupId("G"))), baselines)
		} finally {
			scope.close()
		}

		assertEquals("Old", tree().group(testGroupId("G"))?.name)
		assertEquals(GroupBaseline("Old"), storedBaselines(baselines)[testGroupId("G")])
		assertEquals(listOf(listOf<Any>("Old")), strRes.argsOf(Res.string.sync_log_account_group_in_use))
	}

	@Test
	fun `deletions run in both directions and retire their baselines`() = runTest {
		val gone = group("DeletedOnServer")
		project("Inside", gone)
		val deletedLocally = server("DeletedLocally")
		val baselines = mapOf(
			testGroupId("DeletedOnServer") to GroupBaseline("DeletedOnServer"),
			deletedLocally.uuid to GroupBaseline("DeletedLocally"),
		)
		coEvery { serverProjectsApi.deleteGroup(deletedLocally.uuid, "sync-1") } returns Result.success("ok")

		val pending = reconcile(setOf(deletedLocally), baselines)
		phase().applyDeletions("sync-1", pending, ledger, onLog = {}, onUnauthorized = {})

		coVerify { serverProjectsApi.deleteGroup(deletedLocally.uuid, "sync-1") }
		assertTrue(tree().groups.isEmpty())
		assertNull(tree().placement[repo.findProject("Inside")!!.id])
		assertTrue(storedBaselines(baselines).isEmpty())
	}
}
