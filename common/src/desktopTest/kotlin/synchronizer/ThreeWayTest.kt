package synchronizer

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.BaselineLedger
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.Winner
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.nameThreeWay
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.threeWay
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ThreeWayTest {

	@Test
	fun `equal sides agree whatever the baseline`() {
		assertEquals(Winner.Agree, threeWay("a", "a", "b", hasBaseline = true))
		assertEquals(Winner.Agree, threeWay("a", "a", null, hasBaseline = false))
	}

	@Test
	fun `no baseline lets the server win`() {
		assertEquals(Winner.TakeServer, threeWay("local", "server", null, hasBaseline = false))
	}

	@Test
	fun `only the server moved, take it`() {
		assertEquals(Winner.TakeServer, threeWay("old", "new", "old", hasBaseline = true))
	}

	@Test
	fun `only the local side moved, push it`() {
		assertEquals(Winner.PushLocal, threeWay("new", "old", "old", hasBaseline = true))
	}

	@Test
	fun `both moved, the server wins`() {
		assertEquals(Winner.TakeServer, threeWay("mine", "theirs", "old", hasBaseline = true))
	}

	@Test
	fun `a null baseline value is a real baseline`() {
		val group = GroupId("g")

		assertEquals(Winner.PushLocal, threeWay(group, null, null, hasBaseline = true))
		assertEquals(Winner.TakeServer, threeWay(null, group, null, hasBaseline = true))
	}

	@Test
	fun `a sanitized local name agrees with the raw server name and baseline`() {
		assertEquals(Winner.Agree, nameThreeWay("Book 1", "Book#1", "Book#1"))
		assertEquals(Winner.PushLocal, nameThreeWay("Renamed", "Book#1", "Book#1"))
		assertEquals(Winner.TakeServer, nameThreeWay("Book 1", "Book#2", "Book#1"))
	}

	@Test
	fun `the ledger merges into stored baselines, keeping what it did not touch`() {
		val kept = GroupId("kept")
		val updated = GroupId("updated")
		val retired = GroupId("retired")
		val stored = ProjectsSynchronizationData(
			groupBaselines = mapOf(
				kept to GroupBaseline("Kept"),
				updated to GroupBaseline("Old"),
				retired to GroupBaseline("Gone"),
			),
			projectBaselines = mapOf(ProjectId("p") to ProjectBaseline("P")),
		)
		val ledger = BaselineLedger()
		ledger.recordGroup(updated, GroupBaseline("New"))
		ledger.retireGroup(retired)
		ledger.retireProject(ProjectId("p"))

		val merged = ledger.applyTo(stored)

		assertEquals(mapOf(kept to GroupBaseline("Kept"), updated to GroupBaseline("New")), merged.groupBaselines)
		assertEquals(emptyMap(), merged.projectBaselines)
	}

	@Test
	fun `a rekeyed group carries its pending baseline and the references to it`() {
		val old = GroupId("old")
		val new = GroupId("new")
		val ledger = BaselineLedger()
		ledger.recordGroup(old, GroupBaseline("Trilogy"))
		ledger.recordGroup(GroupId("child"), GroupBaseline("Child", parentId = old))
		ledger.recordProject(ProjectId("p"), ProjectBaseline("Book", groupId = old))

		ledger.rekeyGroup(old, new)

		val merged = ledger.applyTo(ProjectsSynchronizationData())
		assertEquals(
			mapOf(new to GroupBaseline("Trilogy"), GroupId("child") to GroupBaseline("Child", parentId = new)),
			merged.groupBaselines,
		)
		assertEquals(mapOf(ProjectId("p") to ProjectBaseline("Book", groupId = new)), merged.projectBaselines)
	}

	@Test
	fun `a seeded project baseline is stored unless the session settles or retires it`() {
		val seeded = ProjectId("seeded")
		val settled = ProjectId("settled")
		val ledger = BaselineLedger()
		ledger.seedProjects(mapOf(seeded to ProjectBaseline("Server"), settled to ProjectBaseline("Server")))
		ledger.recordProject(settled, ProjectBaseline("Local"))

		val merged = ledger.applyTo(ProjectsSynchronizationData())

		assertEquals(
			mapOf(seeded to ProjectBaseline("Server"), settled to ProjectBaseline("Local")),
			merged.projectBaselines,
		)
	}
}
