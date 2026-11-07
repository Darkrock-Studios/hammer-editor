package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An event created offline in a timeline another device deletes meanwhile is not lost: the server
 * stores it without checking its timeline, and every device shows it in the Deleted timeline bucket
 * until someone moves it.
 */
class TimelineStragglerTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `an event created offline in a deleted timeline is a straggler everywhere until moved`() = runBlocking {
		val a = newClient("timeline straggler A")
		val doomed = a.createTimeline("Before the evening")
		val doomedEvent = a.createEventOn(doomed, "Doomed")
		assertTrue(a.syncNoConflict(), "A pushes the timeline")

		val b = secondDeviceFor(a, "timeline straggler B")
		assertTrue(b.syncNoConflict(), "B adopts the project")

		val straggler = b.createEventOn(doomed, "Written offline")

		assertTrue(a.timelineService().deleteTimeline(doomed).isSuccess)
		assertTrue(a.syncNoConflict(), "A pushes the deletion")

		assertTrue(b.syncNoConflict(), "B pulls the deletion and pushes its event")
		assertTrue(a.syncNoConflict(), "A pulls B's event")

		listOf(a, b).forEachIndexed { index, client ->
			assertNull(client.projectData().timelineDef(doomed), "client #$index still shows the timeline")
			val orphans = client.orphans()
			assertEquals(listOf(straggler), orphans.map { it.id }, "client #$index stragglers")
			assertEquals(doomed, orphans.single().timelineId, "client #$index straggler keeps its timeline")
			assertTrue(client.events().none { it.id == doomedEvent }, "client #$index kept a deleted event")
		}
		assertConverged(a.projectDef.name, a, b)

		val moved = a.timelineService().moveToTimeline(straggler, TimelineDef.MAIN_ID)
		assertIs<TimelineResult.Ok>(moved)
		assertTrue(a.syncNoConflict(), "A pushes the move")
		assertTrue(b.syncNoConflict(), "B pulls the move")

		listOf(a, b).forEachIndexed { index, client ->
			assertEquals(emptyList(), client.orphans(), "client #$index still has a straggler")
			assertEquals(setOf(straggler), client.eventIdsOn(TimelineDef.MAIN_ID), "client #$index main")
		}

		assertConverged(a.projectDef.name, a, b)
		assertResyncMovesNothing(a)
		assertResyncMovesNothing(b)
	}
}
