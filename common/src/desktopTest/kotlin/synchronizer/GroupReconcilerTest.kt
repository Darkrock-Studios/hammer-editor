package synchronizer

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.Winner
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.AdoptByName
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.reconcileGroups
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.junit.jupiter.api.Test
import testGroupId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupReconcilerTest {

	private fun local(name: String, parent: GroupDef? = null, id: GroupId = testGroupId(name), description: String? = null) =
		GroupDef(
			id = id,
			name = name,
			path = HPath("${parent?.path?.path ?: "/projects"}/$name", name, true),
			parentId = parent?.id,
			description = description,
		)

	private fun server(name: String, parent: GroupId? = null, id: GroupId = testGroupId(name), description: String? = null) =
		ApiGroupDefinition(id, name, parent, description)

	@Test
	fun `a matching tree only agrees and records baselines`() {
		val a = local("A")
		val b = local("B", a, description = "Bee")

		val plan = reconcileGroups(
			listOf(a, b),
			setOf(server("A"), server("B", a.id, description = "Bee")),
			emptyMap(),
		)

		assertTrue(plan.updates.isEmpty())
		assertEquals(
			mapOf(a.id to GroupBaseline("A"), b.id to GroupBaseline("B", a.id, "Bee")),
			plan.agreed,
		)
	}

	@Test
	fun `a nested local chain is created on the server parents first`() {
		val a = local("A")
		val b = local("B", a)
		val c = local("C", b)

		val plan = reconcileGroups(listOf(c, a, b), emptySet(), emptyMap())

		assertEquals(listOf(a, b, c), plan.createOnServer)
	}

	@Test
	fun `a nested server chain is created locally parents first`() {
		val a = server("A")
		val b = server("B", a.uuid)
		val c = server("C", b.uuid)

		val plan = reconcileGroups(emptyList(), setOf(c, b, a), emptyMap())

		assertEquals(listOf(a, b, c), plan.createLocally)
	}

	@Test
	fun `a server group whose parent the server does not list goes at the top level`() {
		val orphan = server("Orphan", GroupId("missing"))

		val plan = reconcileGroups(emptyList(), setOf(orphan), emptyMap())

		assertEquals(listOf(orphan.copy(parentId = null)), plan.createLocally)
	}

	@Test
	fun `a group with a baseline missing on one side was deleted there`() {
		val goneOnServer = local("GoneOnServer")
		val goneLocally = server("GoneLocally")
		val baselines = mapOf(
			goneOnServer.id to GroupBaseline("GoneOnServer"),
			goneLocally.uuid to GroupBaseline("GoneLocally"),
		)

		val plan = reconcileGroups(listOf(goneOnServer), setOf(goneLocally), baselines)

		assertEquals(listOf(goneOnServer), plan.deleteLocally)
		assertEquals(listOf(goneLocally), plan.deleteOnServer)
		assertTrue(plan.createOnServer.isEmpty() && plan.createLocally.isEmpty())
	}

	@Test
	fun `deletions run children first`() {
		val a = local("A")
		val b = local("B", a)
		val baselines = mapOf(a.id to GroupBaseline("A"), b.id to GroupBaseline("B", a.id))

		val plan = reconcileGroups(listOf(a, b), emptySet(), baselines)

		assertEquals(listOf(b, a), plan.deleteLocally)
	}

	@Test
	fun `a new local group adopts a server-only group with its name and parent instead of creating`() {
		val mine = local("Trilogy", id = GroupId("local-id"))
		val theirs = server("Trilogy", id = GroupId("server-id"))

		val plan = reconcileGroups(listOf(mine), setOf(theirs), emptyMap())

		assertEquals(listOf(AdoptByName(mine, theirs.uuid)), plan.adoptions)
		assertTrue(plan.createOnServer.isEmpty())
		assertTrue(plan.createLocally.isEmpty())
	}

	@Test
	fun `adoption needs the same parent`() {
		val parent = local("Parent")
		val mine = local("Trilogy", parent, id = GroupId("local-id"))
		val theirs = server("Trilogy", id = GroupId("server-id"))

		val plan = reconcileGroups(listOf(parent, mine), setOf(server("Parent"), theirs), emptyMap())

		assertTrue(plan.adoptions.isEmpty())
		assertEquals(listOf(mine), plan.createOnServer)
		assertEquals(listOf(theirs), plan.createLocally)
	}

	@Test
	fun `a group excluded from adoption is created instead`() {
		val mine = local("Trilogy", id = GroupId("local-id"))

		val plan = reconcileGroups(listOf(mine), setOf(server("Trilogy", id = GroupId("server-id"))), emptyMap(), setOf(mine.id))

		assertEquals(listOf(mine), plan.createOnServer)
	}

	@Test
	fun `a group moved outside the app is pushed`() {
		val a = local("A")
		val b = local("B", a)
		val baselines = mapOf(a.id to GroupBaseline("A"), b.id to GroupBaseline("B"))

		val plan = reconcileGroups(listOf(a, b), setOf(server("A"), server("B")), baselines)

		val update = plan.updates.single()
		assertEquals(Winner.PushLocal, update.parent)
		assertEquals(Winner.Agree, update.name)
		assertEquals(GroupBaseline("B", a.id), update.baseline)
	}

	@Test
	fun `a server rename is applied`() {
		val a = local("Old")
		val plan = reconcileGroups(listOf(a), setOf(server("New", id = a.id)), mapOf(a.id to GroupBaseline("Old")))

		val update = plan.updates.single()
		assertEquals(Winner.TakeServer, update.name)
		assertEquals(GroupBaseline("New"), update.baseline)
	}

	@Test
	fun `both renamed, the server wins`() {
		val a = local("Mine")
		val plan = reconcileGroups(listOf(a), setOf(server("Theirs", id = a.id)), mapOf(a.id to GroupBaseline("Old")))

		assertEquals(Winner.TakeServer, plan.updates.single().name)
	}

	@Test
	fun `no baseline, the server wins`() {
		val a = local("Mine")
		val plan = reconcileGroups(listOf(a), setOf(server("Theirs", id = a.id)), emptyMap())

		assertEquals(Winner.TakeServer, plan.updates.single().name)
	}

	@Test
	fun `a sanitized local name agrees with its raw server name`() {
		val a = local("Book 1")
		val plan = reconcileGroups(listOf(a), setOf(server("Book#1", id = a.id)), mapOf(a.id to GroupBaseline("Book#1")))

		assertEquals(mapOf(a.id to GroupBaseline("Book#1")), plan.agreed)
	}

	@Test
	fun `a blank description agrees with none`() {
		val a = local("A", description = "  ")
		val plan = reconcileGroups(listOf(a), setOf(server("A", id = a.id)), emptyMap())

		assertEquals(mapOf(a.id to GroupBaseline("A")), plan.agreed)
	}

	@Test
	fun `updates run by server depth, parents first`() {
		val a = local("A")
		val b = local("B", a)
		val baselines = mapOf(a.id to GroupBaseline("A0"), b.id to GroupBaseline("B0", a.id))

		val plan = reconcileGroups(
			listOf(b, a),
			setOf(server("B1", a.id, id = b.id), server("A1", id = a.id)),
			baselines,
		)

		assertEquals(listOf(a.id, b.id), plan.updates.map { it.local.id })
	}
}
