package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression: a sync that pulls down new entities must leave the id allocator fresh. The allocator
 * only re-derived its next id at project-open and sync-start, so a device that adopted a project
 * (downloading entities) and then created something before the next sync minted an id that collided
 * with a just-downloaded entity.
 */
class StaleAllocatorAfterSyncTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `creating an entity right after a download sync does not reuse a downloaded id`() = runBlocking {
		val a = newClient("stale alloc A")
		val scene = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "A Scene"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "alpha"))
		assertTrue(a.syncNoConflict(), "A initial sync")

		val b = secondDeviceFor(a, "stale alloc B")
		assertTrue(b.syncNoConflict(), "B downloads A's scene")

		// No editor re-open here: the adopt sync alone must have refreshed B's allocator.
		val notes: NotesRepository = b.scope.get()
		val created = notes.createNote("note on B")
		assertTrue(isSuccess(created), "note create should succeed")
		assertNotEquals(
			scene.id,
			created.data.id,
			"new entity reused the id of a just-downloaded entity — allocator went stale after the sync",
		)

		assertTrue(b.syncNoConflict(), "B pushes its note")
		assertTrue(a.syncNoConflict(), "A pulls B's note")
		assertConverged(a.projectDef.name, a, b)
	}
}
