package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineDef
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A timeline deletion and an unrelated project data edit made on two devices meet as a 409. The
 * upload carries no common base, so the resolver is asked about the edited field, but the deletion
 * applies whichever side is picked and the timelines field shows no conflict.
 */
class TimelineDeleteRacesProjectDataEditTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `the theme edit syncing second keeps the theme and the deletion`() = runBlocking {
		val (a, b, doomed) = twoDevicesWithTimeline("delete races theme")

		assertTrue(a.timelineService().deleteTimeline(doomed).isSuccess)
		b.setTheme(THEME)

		assertTrue(a.syncNoConflict(), "A pushes the deletion")
		val conflicts = b.syncResolvingProjectData()
		val conflict = conflicts.single()
		assertEquals(THEME, conflict.local.theme)
		assertTrue(doomed in conflict.server.deletedTimelines, "the server side carries the tombstone")
		assertFalse(conflict.showsTimelinesConflict(), "the resolver should show no timelines conflict")

		assertTrue(a.syncNoConflict(), "A pulls the theme")
		assertSettled(doomed, a, b)
	}

	@Test
	@Timeout(value = 180)
	fun `the deletion syncing second keeps the theme and the deletion`() = runBlocking {
		val (a, b, doomed) = twoDevicesWithTimeline("theme races delete")

		assertTrue(a.timelineService().deleteTimeline(doomed).isSuccess)
		b.setTheme(THEME)

		assertTrue(b.syncNoConflict(), "B pushes the theme")
		val conflicts = a.syncResolvingProjectData { conflict ->
			pickEveryLocalField(conflict).copy(theme = conflict.server.theme)
		}
		val conflict = conflicts.single()
		assertEquals(THEME, conflict.server.theme)
		assertTrue(doomed in conflict.local.deletedTimelines, "the local side carries the tombstone")
		assertFalse(conflict.showsTimelinesConflict(), "the resolver should show no timelines conflict")

		assertTrue(b.syncNoConflict(), "B pulls the deletion")
		assertSettled(doomed, a, b)
	}

	private suspend fun twoDevicesWithTimeline(name: String): Triple<HeadlessClient, HeadlessClient, String> {
		val a = newClient("$name A")
		val doomed = a.createTimeline("Before the evening")
		repeat(3) { a.createEventOn(doomed, "Doomed $it") }
		assertTrue(a.syncNoConflict(), "A pushes the timeline")
		val b = secondDeviceFor(a, "$name B")
		assertTrue(b.syncNoConflict(), "B adopts the project")
		assertEquals(3, b.eventIdsOn(doomed).size)
		return Triple(a, b, doomed)
	}

	private suspend fun HeadlessClient.setTheme(theme: ProjectTheme) {
		scope.get<ProjectDataRepository>().updateData { it.copy(theme = theme) }
	}

	private suspend fun assertSettled(doomed: String, a: HeadlessClient, b: HeadlessClient) {
		listOf(a, b).forEachIndexed { index, client ->
			val data = client.projectData()
			assertEquals(THEME, data.theme, "client #$index lost the theme")
			assertNull(data.timelineDef(doomed), "client #$index still shows the timeline")
			assertTrue(data.timelines.none { it.id == doomed }, "client #$index still stores the def")
			assertTrue(doomed in data.deletedTimelines, "client #$index has no tombstone")
			assertEquals(emptySet(), client.eventIdsOn(doomed), "client #$index kept the timeline's events")
			assertEquals(emptyList(), client.orphans(), "client #$index has stragglers")
		}
		assertEquals(a.projectData(), b.projectData(), "project data should match on both devices")
		assertConverged(a.projectDef.name, a, b)
		assertResyncMovesNothing(a)
		assertResyncMovesNothing(b)
	}

	private companion object {
		val THEME = ProjectTheme(primary = "#3A5A40", secondary = "#DAD7CD")
	}
}
