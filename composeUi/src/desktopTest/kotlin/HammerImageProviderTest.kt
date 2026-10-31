import androidx.compose.ui.graphics.ImageBitmap
import com.darkrockstudios.apps.hammer.common.compose.images.HammerImageProvider
import com.darkrockstudios.apps.hammer.common.compose.images.ImageBitmapLoader
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageFileStamp
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.texteditor.richstyle.ImageBlockResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class HammerImageProviderTest {

	private val cat = ImageDef(id = 3, name = "Cat", fileExtension = "png")
	private val images = mutableMapOf(cat.id to cat)
	private val stamps = mutableMapOf(cat.id to ImageFileStamp(size = 10, lastModifiedAtMillis = 1))
	private val changed = MutableSharedFlow<Int>(extraBufferCapacity = 8)

	private val imagesService: ImagesService = mockk {
		every { imageChangedFlow } returns changed
		coEvery { ensureImagesLoaded() } answers { images.values.toList() }
		every { findImage(any()) } answers { images[firstArg()] }
		every { cacheVersion(any()) } answers { stamps[firstArg<ImageDef>().id]?.cacheToken }
		every { imagePath(any()) } answers {
			val def = firstArg<ImageDef>()
			HPath("/images/${def.id}~${def.name}.${def.fileExtension}", "${def.id}~${def.name}.${def.fileExtension}", true)
		}
	}

	/** Each load decodes a new bitmap, so a reload is told apart from the bitmap already shown. */
	private val loads = mutableListOf<Pair<String, String>>()
	private val loader = ImageBitmapLoader { path, version ->
		loads += path to version
		ImageBitmap(4, 3)
	}

	private fun TestScope.provider() =
		HammerImageProvider(imagesService, loader, StandardTestDispatcher(testScheduler))

	@Test
	fun `a source that is not a project image fails without loading`() = runTest {
		val provider = provider()

		val state = provider.resolve("https://example.com/cat.png")
		advanceUntilIdle()

		assertIs<ImageBlockResource.Failed>(state.value)
		assertEquals(emptyList(), loads)
		provider.close()
	}

	@Test
	fun `an unknown image id fails`() = runTest {
		val provider = provider()

		val state = provider.resolve("hammer://image/99")
		advanceUntilIdle()

		assertIs<ImageBlockResource.Failed>(state.value)
		assertEquals(emptyList(), loads)
		provider.close()
	}

	@Test
	fun `a known image loads once and is shared by every lookup`() = runTest {
		val provider = provider()

		val state = provider.resolve("hammer://image/3")
		assertEquals(ImageBlockResource.Loading, state.value)
		advanceUntilIdle()

		assertIs<ImageBlockResource.Loaded>(state.value)
		assertSame(state, provider.resolve("hammer://image/3"))
		advanceUntilIdle()
		assertEquals(listOf("/images/3~Cat.png" to "stamp:10-1"), loads)
		provider.close()
	}

	@Test
	fun `a changed image is loaded again`() = runTest {
		val provider = provider()
		val state = provider.resolve("hammer://image/3")
		advanceUntilIdle()
		val first = (state.value as ImageBlockResource.Loaded).bitmap

		stamps[3] = ImageFileStamp(size = 20, lastModifiedAtMillis = 2)
		changed.emit(3)
		advanceUntilIdle()

		val second = (state.value as ImageBlockResource.Loaded).bitmap
		assertEquals(listOf("stamp:10-1", "stamp:20-2"), loads.map { it.second })
		assert(first !== second)
		provider.close()
	}

	@Test
	fun `a change that leaves the file as it was loads nothing`() = runTest {
		val provider = provider()
		val state = provider.resolve("hammer://image/3")
		advanceUntilIdle()

		changed.emit(3)
		advanceUntilIdle()

		assertIs<ImageBlockResource.Loaded>(state.value)
		assertEquals(1, loads.size)
		provider.close()
	}

	@Test
	fun `a renamed image loads from its new path`() = runTest {
		val provider = provider()
		val state = provider.resolve("hammer://image/3")
		advanceUntilIdle()

		images[3] = cat.copy(name = "Tabby")
		changed.emit(3)
		advanceUntilIdle()

		assertIs<ImageBlockResource.Loaded>(state.value)
		assertEquals("/images/3~Tabby.png", loads.last().first)
		provider.close()
	}

	@Test
	fun `a deleted image fails`() = runTest {
		val provider = provider()
		val state = provider.resolve("hammer://image/3")
		advanceUntilIdle()

		images.remove(3)
		changed.emit(3)
		advanceUntilIdle()

		assertIs<ImageBlockResource.Failed>(state.value)
		provider.close()
	}

	@Test
	fun `an image that cannot be decoded fails`() = runTest {
		val provider = HammerImageProvider(
			imagesService,
			{ _, _ -> null },
			StandardTestDispatcher(testScheduler),
		)

		val state = provider.resolve("hammer://image/3")
		advanceUntilIdle()

		assertIs<ImageBlockResource.Failed>(state.value)
		provider.close()
	}
}
