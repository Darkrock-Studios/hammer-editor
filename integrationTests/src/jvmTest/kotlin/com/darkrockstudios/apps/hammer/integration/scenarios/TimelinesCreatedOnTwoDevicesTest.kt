package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.timelinerepository.liveTimelines
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two devices that each create a timeline offline meet as a 409. Timelines merge by id, so both
 * survive on both devices with their events, whichever side the resolver prefers.
 */
class TimelinesCreatedOnTwoDevicesTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `a timeline created on each device survives on both`() = runBlocking {
		val a = newClient("two timelines A")
		assertTrue(a.syncNoConflict(), "A initial sync")
		val b = secondDeviceFor(a, "two timelines B")
		assertTrue(b.syncNoConflict(), "B adopts the project")

		val upstairs = a.createTimeline("Upstairs")
		a.createEventOn(upstairs, "Upstairs event")
		val village = b.createTimeline("Village")
		b.createEventOn(village, "Village event")

		assertTrue(a.syncNoConflict(), "A pushes its timeline")
		val conflicts = b.syncResolvingProjectData()
		assertTrue(a.syncNoConflict(), "A pulls B's timeline")

		val conflict = conflicts.single()
		assertTrue(conflict.showsTimelinesConflict(), "each side holds a timeline the other lacks")

		val expected = mapOf(upstairs to "Upstairs", village to "Village")
		listOf(a, b).forEachIndexed { index, client ->
			val live = client.projectData().liveTimelines().drop(1).associate { it.id to it.name }
			assertEquals(expected, live, "client #$index timelines")
			val titles = client.events().groupBy({ it.timelineId }, { it.title })
			assertEquals(
				mapOf(upstairs to listOf("Upstairs event"), village to listOf("Village event")),
				titles,
				"client #$index events",
			)
			assertEquals(emptyList(), client.orphans(), "client #$index has stragglers")
		}

		assertEquals(a.projectData(), b.projectData(), "project data should match on both devices")
		assertConverged(a.projectDef.name, a, b)
		assertResyncMovesNothing(a)
		assertResyncMovesNothing(b)
	}
}
