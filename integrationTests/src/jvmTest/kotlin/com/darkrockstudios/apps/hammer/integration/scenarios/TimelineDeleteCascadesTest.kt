package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineDef
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deleting a timeline deletes its events one entity at a time and tombstones its def, so another
 * device loses both in one sync and nothing comes back afterwards.
 */
class TimelineDeleteCascadesTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `a deleted timeline and its events are gone on the other device`() = runBlocking {
		val a = newClient("timeline cascade A")
		val doomed = a.createTimeline("Before the evening")
		val doomedEvents = (1..3).map { a.createEventOn(doomed, "Doomed $it") }.toSet()
		val kept = a.createEventOn(TimelineDef.MAIN_ID, "Kept on main")
		assertTrue(a.syncNoConflict(), "A pushes the timeline")

		val b = secondDeviceFor(a, "timeline cascade B")
		assertTrue(b.syncNoConflict(), "B adopts the project")
		assertNotNull(b.projectData().timelineDef(doomed), "B should have the timeline")
		assertEquals(doomedEvents, b.eventIdsOn(doomed))

		val deleted = assertIs<TimelineDefResult.Ok>(a.timelineService().deleteTimeline(doomed))
		assertEquals(3, deleted.deletedEvents)
		val wire = tapWire()
		assertTrue(a.syncNoConflict(), "A pushes the deletion")
		assertEquals(doomedEvents, wire.entitiesDeleted().toSet(), "A deletes each event on the server")

		assertTrue(b.syncNoConflict(), "B pulls the deletion")

		listOf(a, b).forEachIndexed { index, client ->
			val data = client.projectData()
			assertNull(data.timelineDef(doomed), "client #$index still shows the timeline")
			assertTrue(data.timelines.none { it.id == doomed }, "client #$index still stores the def")
			assertTrue(doomed in data.deletedTimelines, "client #$index has no tombstone")
			val ids = client.events().map { it.id }.toSet()
			assertEquals(setOf(kept), ids, "client #$index should keep only the main event")
			assertEquals(emptyList(), client.orphans(), "client #$index has stragglers")
		}

		assertConverged(a.projectDef.name, a, b)
		assertResyncMovesNothing(a)
		assertResyncMovesNothing(b)
	}
}
