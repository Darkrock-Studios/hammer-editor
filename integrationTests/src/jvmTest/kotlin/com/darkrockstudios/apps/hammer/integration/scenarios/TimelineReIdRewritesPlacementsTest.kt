package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A timeline placement refers to its scene by id, so it has to follow the scene when sync re-IDs it.
 * The rewritten placement must reach the server in the same sync, or the next download would put
 * the old subject back.
 */
class TimelineReIdRewritesPlacementsTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `a re-IDed scene takes its timeline placement along`() = runBlocking {
		val a = newClient("timeline reid A")
		val shared = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "Shared"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(shared, "alpha"))
		assertTrue(a.syncNoConflict(), "A initial sync")

		val b = secondDeviceFor(a, "timeline reid B")
		assertTrue(b.syncNoConflict(), "B adopts the project")

		// A takes id 2, so B's offline scene below collides with it.
		val note = a.scope.get<NotesRepository>().createNote("a note from A")
		assertTrue(isSuccess(note))
		assertEquals(2, note.data.id)
		assertTrue(a.syncNoConflict(), "A pushes its note")

		val placed = assertNotNull(b.sceneEditor.createScene(parent = null, sceneName = "Placed"))
		assertEquals(2, placed.id, "B's scene should collide with A's note")
		b.sceneEditor.storeSceneMarkdownRaw(SceneContent(placed, "landfall"))
		val timelineB: TimeLineRepository = b.scope.get()
		val placementId = b.scope.get<IdAllocator>().claimNextId()
		timelineB.updateEvent(
			TimeLineEvent(
				id = placementId,
				order = 0,
				kind = TimelineKind.SCENE,
				subject = placed.id,
				cachedTitle = "Placed",
			)
		)

		assertTrue(b.syncNoConflict(), "B syncs its colliding scene without a conflict")

		val newSceneId = sceneNamed(b, "Placed").id
		assertNotEquals(2, newSceneId, "B's scene should have been re-IDed past the server's ids")
		assertEquals(newSceneId, placementOn(b).subject)
		assertEquals(newSceneId, timelineB.loadTimeline().events.single().subject, "the rewrite must be stored")

		val c = secondDeviceFor(a, "timeline reid C")
		assertTrue(c.syncNoConflict(), "C downloads the project")
		assertEquals(newSceneId, sceneNamed(c, "Placed").id)
		assertEquals(newSceneId, placementOn(c).subject)

		assertConverged(a.projectDef.name, b, c)
		assertResyncSilent(b)
		assertResyncSilent(c)
	}

	private suspend fun sceneNamed(client: HeadlessClient, name: String): SceneItem =
		client.sceneEditorService.getSceneTree().map { it.value }.single { it.name == name }

	private suspend fun placementOn(client: HeadlessClient): TimeLineEvent =
		client.scope.get<TimeLineRepository>().timelineFlow.first().events.single { it.kind == TimelineKind.SCENE }
}
