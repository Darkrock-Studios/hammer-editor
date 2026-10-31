package components.encyclopedia

import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntryComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddOutcome
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.fakeEntry
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewEntryComponentImagesTest : ComponentTest() {

	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var imageChangedFlow: MutableSharedFlow<Int>
	private lateinit var pickRequests: ImagePickRequests

	private var stored: EntryContent = fakeEntry().copy(images = listOf(3, 5))
	private val hashes = mutableMapOf(3 to "h3", 5 to "h5", 9 to "h9")

	@BeforeEach
	override fun setup() {
		super.setup()

		imageChangedFlow = MutableSharedFlow(extraBufferCapacity = 8)
		pickRequests = ImagePickRequests()
		encyclopediaService = mockk(relaxed = true)
		every { encyclopediaService.entryChangedFlow } returns MutableSharedFlow()
		every { encyclopediaService.imageChangedFlow } returns imageChangedFlow
		every { encyclopediaService.loadEntry(any<EntryDef>()) } answers {
			EntryContainer(stored)
		}
		coEvery { encyclopediaService.entryImageFiles(any()) } answers {
			firstArg<EntryContent>().images.map(::file)
		}

		val referenceIndexService = mockk<ReferenceIndexService>(relaxed = true)
		every { referenceIndexService.flowForEntry(any()) } returns emptyFlow()

		setupComponentKoin(module {
			single { encyclopediaService }
			single { referenceIndexService }
			single<SceneEditorService> { mockk(relaxed = true) }
			single<BackfillEntryReferencesUseCase> { mockk(relaxed = true) }
			single<CleanupReferencesOnEntryDeleteUseCase> { mockk(relaxed = true) }
			single { pickRequests }
		})
	}

	private fun file(id: Int) = ImageFile(
		def = ImageDef(id = id, name = "Image $id", fileExtension = "png"),
		path = HPath(path = "/images/$id~Image $id.png", name = "$id~Image $id.png", isAbsolute = true),
		contentHash = hashes[id],
	)

	private fun shownImage(id: Int) = ViewEntry.EntryImage(
		id = id,
		name = "Image $id",
		path = "/images/$id~Image $id.png",
		hash = hashes[id],
	)

	private fun component(
		menus: MutableList<MenuDescriptor> = mutableListOf(),
		componentContext: TestComponentContext = context,
	) = ViewEntryComponent(
		componentContext = componentContext,
		entryDef = stored.toDef(projectDef),
		addMenu = { menus.add(it) },
		removeMenu = {},
		closeEntry = {},
		showScene = {},
		onShowGlobalSearchForTag = {},
	)

	@Test
	fun `The entry's images are shown primary first`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf(shownImage(3), shownImage(5)), comp.state.value.images)
		assertEquals(0, comp.state.value.currentImageIndex)
	}

	@Test
	fun `Importing an image appends it and shows it`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		coEvery { encyclopediaService.importEntryImage(any(), "/staged/new.png") } answers {
			stored = stored.copy(images = stored.images + 9)
			ImageAddOutcome(ImageAddResult.Ok, ImageDef(9, "Test Name", "png"))
		}

		val result = comp.importImage("/staged/new.png")
		advanceUntilIdle()

		assertEquals(ImageAddResult.Ok, result)
		assertEquals(listOf(3, 5, 9), comp.state.value.images.map { it.id })
		assertEquals(2, comp.state.value.currentImageIndex)
		coVerify(exactly = 1) { encyclopediaService.importEntryImage(stored.toDef(projectDef), "/staged/new.png") }
	}

	@Test
	fun `A failed import reports why and changes nothing`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.showImageAt(1)
		coEvery { encyclopediaService.importEntryImage(any(), any<String>()) } returns
			ImageAddOutcome(ImageAddResult.TooLarge)

		val result = comp.importImage("/staged/huge.png")
		advanceUntilIdle()

		assertEquals(ImageAddResult.TooLarge, result)
		assertEquals(listOf(3, 5), comp.state.value.images.map { it.id })
		assertEquals(1, comp.state.value.currentImageIndex)
	}

	@Test
	fun `Removing an image detaches it from the entry`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.showImageAt(1)
		coEvery { encyclopediaService.removeEntryImage(any(), 5) } answers {
			stored = stored.copy(images = listOf(3))
			true
		}

		comp.removeImage(5)
		advanceUntilIdle()

		assertEquals(listOf(3), comp.state.value.images.map { it.id })
		assertEquals(0, comp.state.value.currentImageIndex)
		coVerify(exactly = 1) { encyclopediaService.removeEntryImage(stored.toDef(projectDef), 5) }
	}

	@Test
	fun `Setting the primary image moves it first and keeps it on show`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.showImageAt(1)
		coEvery { encyclopediaService.setPrimaryImage(any(), 5) } answers {
			stored = stored.copy(images = listOf(5, 3))
			true
		}

		comp.setPrimaryImage(5)
		advanceUntilIdle()

		assertEquals(listOf(5, 3), comp.state.value.images.map { it.id })
		assertEquals(0, comp.state.value.currentImageIndex)
	}

	@Test
	fun `The menu's remove asks about the image on show`() = runTest(mainTestDispatcher) {
		val menus = mutableListOf<MenuDescriptor>()
		val comp = component(menus)
		context.resume()
		advanceUntilIdle()

		comp.showImageAt(1)
		val remove = menus.single().items.single { it.id == "view-entry-remove-image" }
		remove.action(remove.id)
		assertEquals(5, comp.state.value.removeImageConfirm)

		comp.dismissRemoveImageConfirm()
		assertNull(comp.state.value.removeImageConfirm)
	}

	@Test
	fun `A change to one of the entry's images reloads them, others do not`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()
		coVerify(exactly = 1) { encyclopediaService.entryImageFiles(any()) }

		imageChangedFlow.emit(42)
		advanceUntilIdle()
		coVerify(exactly = 1) { encyclopediaService.entryImageFiles(any()) }

		hashes[5] = "replaced"
		imageChangedFlow.emit(5)
		advanceUntilIdle()

		assertEquals("replaced", comp.state.value.images.single { it.id == 5 }.hash)
	}

	private val libraryTarget get() = ImagePickTarget.EntryImage(stored.id)

	private fun givenAddEntryImageAppends() {
		coEvery { encyclopediaService.addEntryImage(any(), any()) } answers {
			stored = stored.copy(images = stored.images + secondArg<Int>())
			true
		}
	}

	@Test
	fun `Adding from the library asks the gallery and shows the picked image`() = runTest(mainTestDispatcher) {
		val requested = mutableListOf<ImagePickTarget>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pickRequests.requests.collect { requested += it } }
		givenAddEntryImageAppends()
		val comp = component()
		context.resume()
		advanceUntilIdle()

		comp.addImageFromLibrary()
		advanceUntilIdle()
		assertEquals(listOf<ImagePickTarget>(libraryTarget), requested)
		assertTrue(comp.state.value.pendingLibraryPick)

		pickRequests.deliver(libraryTarget, 9)
		advanceUntilIdle()

		coVerify(exactly = 1) { encyclopediaService.addEntryImage(stored.toDef(projectDef), 9) }
		assertEquals(listOf(3, 5, 9), comp.state.value.images.map { it.id })
		assertEquals(2, comp.state.value.currentImageIndex)
		assertFalse(comp.state.value.pendingLibraryPick)
	}

	@Test
	fun `A cancelled library pick adds nothing`() = runTest(mainTestDispatcher) {
		val comp = component()
		context.resume()
		advanceUntilIdle()

		comp.addImageFromLibrary()
		advanceUntilIdle()
		pickRequests.deliver(libraryTarget, null)
		advanceUntilIdle()

		coVerify(exactly = 0) { encyclopediaService.addEntryImage(any(), any()) }
		assertFalse(comp.state.value.pendingLibraryPick)
		assertEquals(listOf(3, 5), comp.state.value.images.map { it.id })
	}

	@Test
	fun `A library pick survives process death`() = runTest(mainTestDispatcher) {
		givenAddEntryImageAppends()
		val comp = component()
		context.resume()
		advanceUntilIdle()
		comp.addImageFromLibrary()
		advanceUntilIdle()

		val restoredContext = context.saveAndRecreate()
		context.destroy()
		val restored = component(componentContext = restoredContext)
		restoredContext.resume()
		advanceUntilIdle()
		assertTrue(restored.state.value.pendingLibraryPick)

		pickRequests.deliver(libraryTarget, 9)
		advanceUntilIdle()

		assertEquals(listOf(3, 5, 9), restored.state.value.images.map { it.id })
		assertFalse(restored.state.value.pendingLibraryPick)
	}
}
