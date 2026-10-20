package synchronizer

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeAction.MoveLocally
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeAction.MoveOnServer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeAction.RenameLocally
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectAttributeAction.RenameOnServer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.nearestLocalGroup
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.reconcileProjectAttributes
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.seedFromLegacyRenames
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.RenamedProject
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.junit.jupiter.api.Test
import testGroupId
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectAttributeReconcilerTest {

	private val group = testGroupId("Trilogy")

	private fun project(name: String, id: ProjectId = testProjectId("p")) =
		ProjectDef(id, name, HPath("/projects/$name", name, true))

	private fun reconcile(
		local: ProjectDef,
		localGroup: GroupId?,
		server: ApiProjectDefinition,
		baseline: ProjectBaseline?,
		excluded: Set<ProjectId> = emptySet(),
		confirmed: Set<GroupId> = setOf(group),
	) = reconcileProjectAttributes(
		local = listOf(local),
		placement = mapOf(local.id to localGroup),
		server = listOf(server),
		baselines = baseline?.let { mapOf(local.id to it) } ?: emptyMap(),
		excluded = excluded,
		confirmedGroupIds = confirmed,
	)

	@Test
	fun `a rename made outside the app is pushed`() {
		val local = project("New")

		val result = reconcile(local, null, ApiProjectDefinition("Old", local.id), ProjectBaseline("Old")).single()

		assertEquals(listOf(RenameOnServer("New")), result.actions)
		assertEquals(ProjectBaseline("New"), result.baseline)
	}

	@Test
	fun `a server rename is applied locally, sanitized`() {
		val local = project("Old")

		val result = reconcile(local, null, ApiProjectDefinition("New#1", local.id), ProjectBaseline("Old")).single()

		assertEquals(listOf(RenameLocally("New 1")), result.actions)
		assertEquals(ProjectBaseline("New#1"), result.baseline)
	}

	@Test
	fun `both renamed, the server wins`() {
		val local = project("Mine")

		val result = reconcile(local, null, ApiProjectDefinition("Theirs", local.id), ProjectBaseline("Old")).single()

		assertEquals(listOf(RenameLocally("Theirs")), result.actions)
	}

	@Test
	fun `no baseline, the server wins`() {
		val local = project("Mine")

		val result = reconcile(local, group, ApiProjectDefinition("Theirs", local.id), baseline = null).single()

		assertEquals(listOf(RenameLocally("Theirs"), MoveLocally(null)), result.actions)
	}

	@Test
	fun `a move made outside the app is pushed`() {
		val local = project("Book")

		val result = reconcile(local, group, ApiProjectDefinition("Book", local.id), ProjectBaseline("Book")).single()

		assertEquals(listOf(MoveOnServer(group)), result.actions)
		assertEquals(ProjectBaseline("Book", group), result.baseline)
	}

	@Test
	fun `a server move is applied locally`() {
		val local = project("Book")

		val result = reconcile(local, null, ApiProjectDefinition("Book", local.id, group), ProjectBaseline("Book")).single()

		assertEquals(listOf(MoveLocally(group)), result.actions)
		assertEquals(ProjectBaseline("Book", group), result.baseline)
	}

	@Test
	fun `a move into a group the server does not hold is neither pushed nor settled`() {
		val local = project("Book")

		val result = reconcile(
			local, group, ApiProjectDefinition("Book", local.id), ProjectBaseline("Book"), confirmed = emptySet(),
		).single()

		assertTrue(result.actions.isEmpty())
		assertNull(result.baseline)
	}

	@Test
	fun `an excluded project yields nothing`() {
		val local = project("Book")

		val result = reconcile(local, null, ApiProjectDefinition("Other", local.id), null, excluded = setOf(local.id))

		assertTrue(result.isEmpty())
	}

	@Test
	fun `a project on one side only yields nothing`() {
		val result = reconcileProjectAttributes(
			local = listOf(project("Local", ProjectId("a"))),
			placement = emptyMap(),
			server = listOf(ApiProjectDefinition("Server", ProjectId("b"))),
			baselines = emptyMap(),
			excluded = emptySet(),
			confirmedGroupIds = emptySet(),
		)

		assertTrue(result.isEmpty())
	}

	@Test
	fun `a legacy queued rename is pushed once seeded`() {
		val local = project("Renamed")
		val server = ApiProjectDefinition("Original", local.id)
		val dead = ProjectId("dead")

		val seeds = seedFromLegacyRenames(
			setOf(RenamedProject(local.id, "Renamed"), RenamedProject(dead, "Whatever")),
			listOf(server),
			emptyMap(),
		)
		val result = reconcile(local, null, server, seeds[local.id]).single()

		assertEquals(mapOf(local.id to ProjectBaseline("Original")), seeds)
		assertEquals(listOf(RenameOnServer("Renamed")), result.actions)
	}

	@Test
	fun `the nearest local ancestor stands in for a server group this device lacks`() {
		val top = GroupDef(testGroupId("Top"), "Top", HPath("/projects/Top", "Top", true), null, null)
		val tree = ProjectTree(listOf(top), emptyList(), emptyMap())
		val serverGroups = listOf(
			ApiGroupDefinition(top.id, "Top"),
			ApiGroupDefinition(testGroupId("Mid"), "Mid", top.id),
			ApiGroupDefinition(testGroupId("Leaf"), "Leaf", testGroupId("Mid")),
		).associateBy { it.uuid }

		assertEquals(top, tree.nearestLocalGroup(testGroupId("Leaf"), serverGroups))
		assertNull(tree.nearestLocalGroup(GroupId("unknown"), serverGroups))
		assertNull(tree.nearestLocalGroup(null, serverGroups))
	}
}
