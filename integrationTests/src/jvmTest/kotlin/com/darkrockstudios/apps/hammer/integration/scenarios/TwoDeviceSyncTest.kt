package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two real devices on one account, one server project. Exercises the realistic multi-actor path the
 * single-client tests can't: a second device adopting the project, and independent edits on each
 * device converging. Assertions use the model-free oracle — after everyone settles, every device
 * holds exactly what the server holds, and an extra sync is silent.
 */
class TwoDeviceSyncTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `a second device downloads the first device's project`() = runBlocking {
		val a = newClient("two device download A")
		val scene = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "From A"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "alpha"))
		assertTrue(a.syncNoConflict(), "first device sync should succeed")

		val b = secondDeviceFor(a, "two device download B")
		assertTrue(b.syncNoConflict(), "second device sync should succeed")

		assertConverged(a.projectDef.name, a, b)
		val bScene = assertNotNull(b.sceneEditor.getSceneItemFromId(scene.id), "B should have A's scene")
		assertEquals("alpha", b.sceneEditor.loadSceneMarkdownRaw(bScene), "content should round-trip to B")
		assertResyncSilent(b)
	}

	@Test
	@Timeout(value = 120)
	fun `independent edits on two devices converge`() = runBlocking {
		val a = newClient("two device merge A")
		val sceneA = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "Scene One"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(sceneA, "one"))
		assertTrue(a.syncNoConflict(), "A initial sync")

		val b = secondDeviceFor(a, "two device merge B")
		assertTrue(b.syncNoConflict(), "B adopts the project")
		// A real second device re-derives its next id from the now-populated project on open;
		// without this the allocator is stale from the empty-project sync start and the new scene
		// collides with the just-downloaded one.
		b.sceneEditor.initializeSceneEditor()

		// Independent work on each device, touching different entities.
		val notesA: NotesRepository = a.scope.get()
		notesA.createNote("a note from A")
		val sceneB = assertNotNull(b.sceneEditor.createScene(parent = null, sceneName = "Scene Two"))
		b.sceneEditor.storeSceneMarkdownRaw(SceneContent(sceneB, "two"))

		// Round-trip the changes through the server: A up, B up, then A again to pull B's work.
		assertTrue(a.syncNoConflict(), "A pushes its note")
		assertTrue(b.syncNoConflict(), "B pushes its scene")
		assertTrue(a.syncNoConflict(), "A pulls B's scene")

		assertConverged(a.projectDef.name, a, b)
		assertResyncSilent(a)
		assertResyncSilent(b)
	}
}
