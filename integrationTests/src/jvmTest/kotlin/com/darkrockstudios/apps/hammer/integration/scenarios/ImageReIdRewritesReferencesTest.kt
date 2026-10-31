package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import com.darkrockstudios.apps.hammer.integration.TestImages
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Markdown refers to an image by its entity id, so the text has to follow the image when sync
 * re-IDs it or deletes it. The rewritten scenes must reach the server in the same sync, or the next
 * download would put the old references back.
 */
class ImageReIdRewritesReferencesTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `a re-IDed image takes the references to it along`() = runBlocking {
		val a = newClient("image reid A")
		val shared = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "Shared"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(shared, "alpha"))
		assertTrue(a.syncNoConflict(), "A initial sync")

		val b = secondDeviceFor(a, "image reid B")
		assertTrue(b.syncNoConflict(), "B adopts the project")

		// A takes id 2, so B's offline image below collides with it.
		val notesA: NotesRepository = a.scope.get()
		val note = notesA.createNote("a note from A")
		assertTrue(isSuccess(note))
		assertEquals(2, note.data.id)
		assertTrue(a.syncNoConflict(), "A pushes its note")

		val imagesB: ImagesRepository = b.scope.get()
		val bytes = TestImages.png(7)
		val image = assertNotNull(imagesB.create("cover", bytes, "png").image)
		assertEquals(2, image.id, "B's image should collide with A's note")
		val newScene = assertNotNull(b.sceneEditor.createScene(parent = null, sceneName = "With cover"))
		assertEquals(3, newScene.id)
		b.sceneEditor.storeSceneMarkdownRaw(SceneContent(newScene, "![cover](hammer://image/2)\n\nbody"))
		// Through the editor's buffer, as an open editor would hold it.
		val sharedOnB = assertNotNull(b.sceneEditor.getSceneItemFromId(shared.id))
		assertTrue(b.sceneEditorService.replaceSceneText(sharedOnB, "alpha\n\n![cover](hammer://image/2)"))

		assertTrue(b.syncNoConflict(), "B syncs its colliding image without a conflict")

		val imagesAfter = imagesB.loadImages()
		val moved = assertNotNull(imagesAfter.find { it.name == "cover" })
		assertEquals(4, moved.id, "B's image should have been re-IDed past the server's ids")
		assertEquals("![cover](hammer://image/4)\n\nbody", sceneText(b, newScene.id))
		assertEquals("alpha\n\n![cover](hammer://image/4)", sceneText(b, shared.id))
		assertEquals(
			"alpha\n\n![cover](hammer://image/4)",
			b.sceneEditorService.getCurrentSceneContentOrNull(sharedOnB),
			"the buffer an open editor shows should follow too",
		)

		val c = secondDeviceFor(a, "image reid C")
		assertTrue(c.syncNoConflict(), "C downloads the project")
		assertEquals("![cover](hammer://image/4)\n\nbody", sceneText(c, newScene.id))
		assertEquals("alpha\n\n![cover](hammer://image/4)", sceneText(c, shared.id))
		val imagesC: ImagesRepository = c.scope.get()
		val onC = assertNotNull(imagesC.loadImages().find { it.id == 4 }, "C should have the image as id 4")
		assertContentEquals(bytes, imagesC.loadImageBytes(onC))

		assertTrue(a.syncNoConflict(), "A pulls B's work")
		assertEquals("alpha\n\n![cover](hammer://image/4)", sceneText(a, shared.id))

		assertConverged(a.projectDef.name, a, b, c)
		assertResyncSilent(b)
		assertResyncSilent(c)
	}

	@Test
	@Timeout(value = 180)
	fun `deleting a used image takes it out of the text on every device`() = runBlocking {
		val a = newClient("image delete A")
		val imagesA: ImagesRepository = a.scope.get()
		val image = assertNotNull(imagesA.create("map", TestImages.png(3), "png").image)
		val scene = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "Journey"))
		a.sceneEditor.storeSceneMarkdownRaw(
			SceneContent(scene, "Before\n\n![map](hammer://image/${image.id})\n\nAfter"),
		)
		val notesA: NotesRepository = a.scope.get()
		val note = notesA.createNote("Route\n\n![map](hammer://image/${image.id})")
		assertTrue(isSuccess(note))
		assertTrue(a.syncNoConflict(), "A initial sync")

		val b = secondDeviceFor(a, "image delete B")
		assertTrue(b.syncNoConflict(), "B adopts the project")
		assertEquals("Before\n\n![map](hammer://image/${image.id})\n\nAfter", sceneText(b, scene.id))

		val deleted = a.scope.get<ImagesService>().delete(image.id)
		assertTrue(deleted.deleted)
		assertEquals("Before\n\nAfter", sceneText(a, scene.id))
		assertTrue(a.syncNoConflict(), "A pushes the delete")

		assertTrue(b.syncNoConflict(), "B pulls the delete")
		val imagesB: ImagesRepository = b.scope.get()
		assertNull(imagesB.loadImages().find { it.id == image.id }, "B should no longer have the image")
		assertEquals("Before\n\nAfter", sceneText(b, scene.id))
		val noteOnB = b.scope.get<NotesRepository>().loadNotesImperative().single { it.note.id == note.data.id }
		assertEquals("Route\n", noteOnB.note.content)
		assertFalse("hammer://image" in noteOnB.note.content)

		assertConverged(a.projectDef.name, a, b)
		assertResyncSilent(a)
		assertResyncSilent(b)
	}

	private fun sceneText(client: HeadlessClient, sceneId: Int): String {
		val scene: SceneItem = assertNotNull(client.sceneEditor.getSceneItemFromId(sceneId), "scene $sceneId")
		return client.sceneEditor.loadSceneMarkdownRaw(scene)
	}
}
