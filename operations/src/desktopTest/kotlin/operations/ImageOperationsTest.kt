package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.core.Entry
import com.darkrockstudios.apps.hammer.operations.core.EntryCreateInput
import com.darkrockstudios.apps.hammer.operations.core.EntryImageInput
import com.darkrockstudios.apps.hammer.operations.core.EntryImages
import com.darkrockstudios.apps.hammer.operations.core.EntryKind
import com.darkrockstudios.apps.hammer.operations.core.EntryUpdateInput
import com.darkrockstudios.apps.hammer.operations.core.ImageAddInput
import com.darkrockstudios.apps.hammer.operations.core.ImageContent
import com.darkrockstudios.apps.hammer.operations.core.ImageDeleted
import com.darkrockstudios.apps.hammer.operations.core.ImageRenameInput
import com.darkrockstudios.apps.hammer.operations.core.ImageReplaceInput
import com.darkrockstudios.apps.hammer.operations.core.ImageSummary
import com.darkrockstudios.apps.hammer.operations.core.ImageUser
import com.darkrockstudios.apps.hammer.operations.core.ImageUserKind
import com.darkrockstudios.apps.hammer.operations.core.Images
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import org.koin.core.component.get
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageOperationsTest : KoinOperationsTest() {

	private lateinit var def: ProjectDef

	@BeforeEach
	fun setUp() = onTestThread {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		check(created is ClientResult.Success<ProjectDef>)
		def = created.data
	}

	private fun failsWith(kind: OperationException.Kind, block: suspend () -> Unit) = onTestThread {
		assertEquals(kind, assertFailsWith<OperationException> { block() }.kind)
	}

	private suspend fun list() = run<ProjectInput, Images>("image.list", ProjectInput(PROJECT)).images

	@Test
	fun `images are added, read, renamed, and replaced`() = onTestThread {
		val added = run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Lighthouse", "PNG", PNG))
		assertEquals(ImageSummary(added.id, "Lighthouse", "png", inUse = false), added)
		assertEquals(listOf(added), list())

		val read = run<ProjectItemInput, ImageContent>("image.get", ProjectItemInput(PROJECT, added.id))
		assertEquals("png", read.extension)
		assertContentEquals(PNG, read.content)

		val renamed = run<ImageRenameInput, ImageSummary>("image.rename", ImageRenameInput(PROJECT, added.id, "Beacon"))
		assertEquals("Beacon", renamed.name)

		val replaced = run<ImageReplaceInput, ImageSummary>("image.replace", ImageReplaceInput(PROJECT, added.id, "jpg", JPEG))
		assertEquals(ImageSummary(added.id, "Beacon", "jpg", inUse = false), replaced)
		assertContentEquals(JPEG, run<ProjectItemInput, ImageContent>("image.get", ProjectItemInput(PROJECT, added.id)).content)
	}

	@Test
	fun `deleting an image takes it out of the entries that use it`() = onTestThread {
		val image = run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Map", "png", PNG))
		val keep = run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Coast", "png", PNG))
		val alice = run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Alice", EntryKind.Person, ""))
		val point = run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Point", EntryKind.Place, ""))
		val bay = run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Bay", EntryKind.Place, ""))
		run<EntryImageInput, EntryImages>("entry.images.add", EntryImageInput(PROJECT, alice.id, image.id))
		run<EntryImageInput, EntryImages>("entry.images.add", EntryImageInput(PROJECT, alice.id, keep.id))
		val text = "Before.\n\n![Map](hammer://image/${image.id})\n\nAfter."
		run<EntryUpdateInput, Entry>("entry.update", EntryUpdateInput(PROJECT, point.id, text))

		assertEquals(listOf(true, true), list().map { it.inUse })
		val renamed = run<ImageRenameInput, ImageSummary>("image.rename", ImageRenameInput(PROJECT, keep.id, "Shore"))
		assertTrue(renamed.inUse)

		val deleted = run<ProjectItemInput, ImageDeleted>("image.delete", ProjectItemInput(PROJECT, image.id))

		assertEquals(
			listOf(ImageUser(ImageUserKind.Entry, alice.id), ImageUser(ImageUserKind.Entry, point.id)),
			deleted.removedFrom,
		)
		assertFalse(bay.id in deleted.removedFrom.map { it.id })
		assertEquals(listOf(keep.id), run<ProjectItemInput, Entry>("entry.read", ProjectItemInput(PROJECT, alice.id)).images)
		assertEquals("Before.\n\nAfter.", run<ProjectItemInput, Entry>("entry.read", ProjectItemInput(PROJECT, point.id)).text)
		assertEquals(listOf(keep.id), list().map { it.id })
	}

	@Test
	fun `deleting the cover image clears the cover and reports it`() = onTestThread {
		val image = run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Cover", "png", PNG))
		val scope = openProjectScope(def)
		val projectData = scope.get<ProjectDataRepository>()
		projectData.updateData { it.copy(coverImageId = image.id) }
		assertTrue(list().single().inUse)

		val deleted = run<ProjectItemInput, ImageDeleted>("image.delete", ProjectItemInput(PROJECT, image.id))

		assertEquals(listOf(ImageUser(ImageUserKind.Cover)), deleted.removedFrom)
		assertNull(projectData.load().data.coverImageId)
		closeProjectScope(scope, def)
	}

	@Test
	fun `bad images and names are invalid`() {
		failsWith(OperationException.Kind.InvalidInput) {
			run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Map", "jpg", PNG))
		}
		failsWith(OperationException.Kind.InvalidInput) {
			run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Map", "bmp", PNG))
		}
		failsWith(OperationException.Kind.InvalidInput) {
			run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Map", "png", PNG + ByteArray(9 * 1024 * 1024)))
		}
		val image = onTestThread { run<ImageAddInput, ImageSummary>("image.add", ImageAddInput(PROJECT, "Map", "png", PNG)) }
		failsWith(OperationException.Kind.InvalidInput) {
			run<ImageRenameInput, ImageSummary>("image.rename", ImageRenameInput(PROJECT, image.id, " "))
		}
		failsWith(OperationException.Kind.InvalidInput) {
			run<ImageReplaceInput, ImageSummary>("image.replace", ImageReplaceInput(PROJECT, image.id, "gif", PNG))
		}
	}

	@Test
	fun `images that are not there are not found`() {
		failsWith(OperationException.Kind.NotFound) {
			run<ProjectItemInput, ImageContent>("image.get", ProjectItemInput(PROJECT, 404))
		}
		failsWith(OperationException.Kind.NotFound) {
			run<ImageRenameInput, ImageSummary>("image.rename", ImageRenameInput(PROJECT, 404, "Map"))
		}
		failsWith(OperationException.Kind.NotFound) {
			run<ImageReplaceInput, ImageSummary>("image.replace", ImageReplaceInput(PROJECT, 404, "png", PNG))
		}
		failsWith(OperationException.Kind.NotFound) {
			run<ProjectItemInput, ImageDeleted>("image.delete", ProjectItemInput(PROJECT, 404))
		}
	}

	private companion object {
		const val PROJECT = "Lighthouse"
		val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
		val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)
	}
}
