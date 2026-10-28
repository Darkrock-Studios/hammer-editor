package components.images

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.components.images.ImageGallery
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryComponent
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickResult
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddOutcome
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageDeleteResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsage
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryComponentTest : ComponentTest() {

	private lateinit var imagesService: ImagesService
	private lateinit var imagesFlow: MutableSharedFlow<List<ImageDef>>
	private lateinit var pickRequests: ImagePickRequests
	private var stored = listOf(def(3, "Map"), def(5, "Coast"), def(8, "Bay"))
	private var used = setOf(3)
	private var dismissed = 0

	private val target = ImagePickTarget.EntryImage(entryId = 21)

	@BeforeEach
	override fun setup() {
		super.setup()
		dismissed = 0
		imagesFlow = MutableSharedFlow(replay = 1)
		imagesFlow.tryEmit(stored)
		pickRequests = ImagePickRequests()
		imagesService = mockk(relaxed = true)
		every { imagesService.imagesFlow } returns imagesFlow
		coEvery { imagesService.ensureImagesLoaded() } answers { stored }
		every { imagesService.imageFile(any()) } answers { file(firstArg()) }
		coEvery { imagesService.findUsedImageIds() } answers { used }

		setupComponentKoin(module {
			single { imagesService }
			single { pickRequests }
		})
	}

	private fun def(id: Int, name: String, ext: String = "png") = ImageDef(id, name, ext)

	private fun file(def: ImageDef) = ImageFile(
		def = def,
		path = HPath(path = "/images/${def.id}~${def.name}.${def.fileExtension}", name = "x", isAbsolute = true),
		contentHash = "h${def.id}-${def.fileExtension}",
	)

	private fun component(mode: ImageGalleryMode = ImageGalleryMode.Browse) =
		ImageGalleryComponent(context, projectDef, mode, onDismiss = { dismissed++ })

	private fun TestScope.collectResults(): MutableList<ImagePickResult> {
		val results = mutableListOf<ImagePickResult>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pickRequests.results.collect { results += it } }
		return results
	}

	@Test
	fun `The project's images are listed by name with whether they are used`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()

		val state = comp.state.value
		assertTrue(state.loaded)
		assertEquals(listOf("Bay", "Coast", "Map"), state.images.map { it.def.name })
		assertEquals(listOf(false, false, true), state.images.map { it.inUse })
		assertEquals("/images/3~Map.png", state.images.last().path)
		assertEquals("h3-png", state.images.last().hash)
	}

	@Test
	fun `The unused filter hides used images and checks use again`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()

		used = setOf(3, 5)
		comp.toggleFilterUnused()
		advanceUntilIdle()

		assertEquals(listOf(8), comp.state.value.visibleImages.map { it.def.id })
		coVerify(exactly = 2) { imagesService.findUsedImageIds() }

		comp.toggleFilterUnused()
		assertEquals(3, comp.state.value.visibleImages.size)
	}

	@Test
	fun `Adding an image selects it`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		coEvery { imagesService.addFromPath("/staged/pier.png") } answers {
			stored = stored + def(13, "pier")
			ImageAddOutcome(ImageAddResult.Ok, def(13, "pier"))
		}

		val result = comp.addImage("/staged/pier.png")
		advanceUntilIdle()

		assertEquals(ImageAddResult.Ok, result)
		assertEquals(13, comp.state.value.selectedId)
		assertEquals(false, comp.state.value.selected?.inUse)
		assertFalse(comp.state.value.busy)
	}

	@Test
	fun `A failed add reports why and selects nothing`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		coEvery { imagesService.addFromPath(any()) } returns ImageAddOutcome(ImageAddResult.TooLarge)

		assertEquals(ImageAddResult.TooLarge, comp.addImage("/staged/huge.png"))
		assertNull(comp.state.value.selectedId)
	}

	@Test
	fun `Replacing an image keeps its id and shows its new hash`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.select(5)
		coEvery { imagesService.replaceFromPath(5, "/staged/coast.jpg") } answers {
			stored = stored.map { if (it.id == 5) it.copy(fileExtension = "jpg") else it }
			ImageAddOutcome(ImageAddResult.Ok, def(5, "Coast", "jpg"))
		}

		assertEquals(ImageAddResult.Ok, comp.replaceImage(5, "/staged/coast.jpg"))
		advanceUntilIdle()

		val selected = comp.state.value.selected
		assertEquals(5, selected?.def?.id)
		assertEquals("h5-jpg", selected?.hash)
	}

	@Test
	fun `Renaming closes the dialog only when it works`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.startRename(5)
		coEvery { imagesService.rename(5, "bad/name") } returns ImageAddOutcome(ImageAddResult.NameInvalid)
		coEvery { imagesService.rename(5, "Shore") } answers {
			stored = stored.map { if (it.id == 5) it.copy(name = "Shore") else it }
			ImageAddOutcome(ImageAddResult.Ok, def(5, "Shore"))
		}

		assertEquals(ImageAddResult.NameInvalid, comp.rename(5, "bad/name"))
		advanceUntilIdle()
		assertEquals(5, comp.state.value.renaming)

		assertEquals(ImageAddResult.Ok, comp.rename(5, "Shore"))
		advanceUntilIdle()
		assertNull(comp.state.value.renaming)
		assertTrue(comp.state.value.images.any { it.def.name == "Shore" })
	}

	@Test
	fun `Asking to delete finds the image's usages, confirming deletes it`() = runTest(mainTestDispatcher) {
		val usages = ImageUsages(listOf(ImageUsage(TouchedEntity(EntityType.EncyclopediaEntry, 30), "Alice")))
		coEvery { imagesService.findUsages(3) } returns usages
		coEvery { imagesService.delete(3) } answers {
			stored = stored.filterNot { it.id == 3 }
			ImageDeleteResult(deleted = true, touched = setOf(TouchedEntity(EntityType.EncyclopediaEntry, 30)))
		}
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.select(3)

		comp.requestDelete(3)
		advanceUntilIdle()
		assertEquals(ImageGallery.DeleteRequest(3, usages), comp.state.value.confirmDelete)
		coVerify(exactly = 0) { imagesService.delete(any()) }

		comp.confirmDelete()
		advanceUntilIdle()

		coVerify(exactly = 1) { imagesService.delete(3) }
		assertNull(comp.state.value.confirmDelete)
		assertNull(comp.state.value.selectedId)
		assertEquals(listOf(8, 5), comp.state.value.images.map { it.def.id })
	}

	@Test
	fun `Cancelling a delete deletes nothing`() = runTest(mainTestDispatcher) {
		coEvery { imagesService.findUsages(5) } returns ImageUsages(emptyList())
		val comp = component()
		context.resume()
		advanceUntilIdle()

		comp.requestDelete(5)
		advanceUntilIdle()
		comp.cancelDelete()
		comp.confirmDelete()
		advanceUntilIdle()

		coVerify(exactly = 0) { imagesService.delete(any()) }
	}

	@Test
	fun `Picking delivers the image to the target and closes`() = runTest(mainTestDispatcher) {
		val results = collectResults()
		val comp = component(ImageGalleryMode.Pick(target))
		context.resume()
		advanceUntilIdle()

		comp.pick(8)
		advanceUntilIdle()

		assertEquals(listOf(ImagePickResult(target, 8)), results)
		assertEquals(1, dismissed)

		// The slot destroying the gallery afterwards does not deliver again.
		context.destroy()
		advanceUntilIdle()
		assertEquals(listOf(ImagePickResult(target, 8)), results)
	}

	@Test
	fun `Closing a pick delivers no image`() = runTest(mainTestDispatcher) {
		val results = collectResults()
		val comp = component(ImageGalleryMode.Pick(target))
		context.resume()
		advanceUntilIdle()

		comp.dismiss()
		advanceUntilIdle()

		assertEquals(listOf(ImagePickResult(target, null)), results)
		assertEquals(1, dismissed)
	}

	@Test
	fun `A pick that is torn down unanswered delivers no image`() = runTest(mainTestDispatcher) {
		val results = collectResults()
		component(ImageGalleryMode.Pick(target))
		context.resume()
		advanceUntilIdle()

		context.destroy()
		advanceUntilIdle()

		assertEquals(listOf(ImagePickResult(target, null)), results)
		assertEquals(0, dismissed)
	}

	@Test
	fun `Browsing neither picks nor delivers`() = runTest(mainTestDispatcher) {
		val results = collectResults()
		val comp = component()
		context.resume()
		advanceUntilIdle()

		comp.pick(8)
		comp.dismiss()
		advanceUntilIdle()

		assertTrue(results.isEmpty())
		assertEquals(1, dismissed)
	}
}
