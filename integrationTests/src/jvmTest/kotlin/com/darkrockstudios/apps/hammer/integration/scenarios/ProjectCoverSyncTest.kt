package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import com.darkrockstudios.apps.hammer.integration.TestImages
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The book cover lives in project data, not in an entity, so it syncs on the project data path.
 * Deleting or re-IDing its image still has to reach every device.
 */
class ProjectCoverSyncTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `a cover follows its image to another device and goes when the image is deleted`() = runBlocking {
		val a = newClient("cover A")
		val image = assertNotNull(a.scope.get<ImagesRepository>().create("cover", TestImages.png(1), "png").image)
		a.projectData().updateData { it.copy(coverImageId = image.id) }
		assertTrue(a.syncNoConflict(), "A pushes the image and the cover")

		val b = secondDeviceFor(a, "cover B")
		assertTrue(b.syncNoConflict(), "B adopts the project")
		assertEquals(image.id, b.coverImageId())
		assertNotNull(b.scope.get<ImagesRepository>().loadImages().find { it.id == image.id })

		val deleted = a.scope.get<ImagesService>().delete(image.id)
		assertTrue(deleted.deleted)
		assertTrue(deleted.coverCleared)
		assertNull(a.coverImageId())
		assertTrue(a.syncNoConflict(), "A pushes the delete")

		assertTrue(b.syncNoConflict(), "B pulls the delete")
		assertNull(b.coverImageId())
		assertNull(b.scope.get<ImagesRepository>().loadImages().find { it.id == image.id })

		assertConverged(a.projectDef.name, a, b)
		assertResyncSilent(a)
		assertResyncSilent(b)
		assertProjectDataResyncSilent(a)
		assertProjectDataResyncSilent(b)
	}

	@Test
	@Timeout(value = 180)
	fun `a re-IDed cover image takes the cover along`() = runBlocking {
		val a = newClient("cover reid A")
		assertTrue(a.syncNoConflict(), "A initial sync")
		val b = secondDeviceFor(a, "cover reid B")
		assertTrue(b.syncNoConflict(), "B adopts the project")

		val note = a.scope.get<NotesRepository>().createNote("a note from A")
		assertTrue(isSuccess(note))
		assertTrue(a.syncNoConflict(), "A pushes its note")

		val image = assertNotNull(b.scope.get<ImagesRepository>().create("cover", TestImages.png(2), "png").image)
		assertEquals(note.data.id, image.id, "B's image should collide with A's note")
		b.projectData().updateData { it.copy(coverImageId = image.id) }

		assertTrue(b.syncNoConflict(), "B syncs its colliding cover image")
		val moved = assertNotNull(b.scope.get<ImagesRepository>().loadImages().find { it.name == "cover" })
		assertTrue(moved.id != image.id, "B's image should have been re-IDed")
		assertEquals(moved.id, b.coverImageId(), "the cover should follow the re-IDed image")

		assertTrue(a.syncNoConflict(), "A pulls B's cover")
		assertEquals(moved.id, a.coverImageId())

		assertConverged(a.projectDef.name, a, b)
		assertResyncSilent(a)
		assertResyncSilent(b)
		assertProjectDataResyncSilent(a)
		assertProjectDataResyncSilent(b)
	}

	private fun HeadlessClient.projectData(): ProjectDataRepository = scope.get()

	private suspend fun HeadlessClient.coverImageId(): Int? = projectData().load().data.coverImageId

	/** An immediate extra sync uploads no project data. */
	private suspend fun assertProjectDataResyncSilent(client: HeadlessClient) {
		val wire = tapWire()
		assertTrue(client.syncNoConflict(), "resync should succeed")
		val uploads = wire.calls.filter { it.method == "POST" && it.path.substringBefore('?').endsWith("/project_data") }
		assertEquals(emptyList(), uploads, "resync re-uploaded project data")
	}
}
