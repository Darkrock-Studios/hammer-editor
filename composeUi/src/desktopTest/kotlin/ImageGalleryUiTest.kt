import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.darkrockstudios.apps.hammer.common.components.images.ImageGallery
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_DELETE_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_DETAIL_IMAGE_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_DETAIL_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_EMPTY_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_SEARCH_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_SORT_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_USE_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_VIEW_TAG
import com.darkrockstudios.apps.hammer.common.images.ImageGalleryUi
import com.darkrockstudios.apps.hammer.common.images.imageGalleryItemTag
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.images.FakeImageGallery
import com.darkrockstudios.apps.hammer.common.preview.images.previewGalleryImages
import com.darkrockstudios.apps.hammer.common.preview.images.previewImageUsages
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageGalleryUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun setGallery(gallery: FakeImageGallery, width: Dp = 1000.dp, height: Dp = 800.dp) {
		compose.setContent {
			KoinApplicationPreview {
				ImageGalleryUi(gallery, modifier = Modifier.size(width = width, height = height))
			}
		}
	}

	// A single click on a thumbnail lands once a double click can no longer follow.
	private fun passDoubleClickWindow() {
		compose.mainClock.advanceTimeBy(DOUBLE_CLICK_WINDOW_MS)
		compose.waitForIdle()
	}

	private fun gallery(mode: ImageGalleryMode, images: List<ImageGallery.GalleryImage> = previewGalleryImages()) =
		FakeImageGallery(
			ImageGallery.State(mode = mode, images = images, loaded = true),
			usages = previewImageUsages(),
		)

	/** Asserts the thumbnails of [ids] are laid out in that reading order. */
	private fun assertShownInOrder(vararg ids: Int) {
		val positions = ids.map { id ->
			val bounds = compose.onNodeWithTag(imageGalleryItemTag(id)).assertIsDisplayed().getUnclippedBoundsInRoot()
			bounds.top to bounds.left
		}
		assertEquals(positions.sortedWith(compareBy({ it.first }, { it.second })), positions)
	}

	@Test
	fun `Pick mode offers USE for the selected image and picks it`() {
		val fake = gallery(ImageGalleryMode.Pick(ImagePickTarget.EntryImage(30)))
		setGallery(fake)

		compose.onNodeWithTag(IMAGE_GALLERY_USE_TAG).assertDoesNotExist()
		compose.onNodeWithTag(imageGalleryItemTag(5)).performClick()
		passDoubleClickWindow()
		compose.onNodeWithTag(IMAGE_GALLERY_USE_TAG).performClick()

		assertEquals(listOf(5), fake.picked)
	}

	@Test
	fun `Double-clicking an image in pick mode picks it`() {
		val fake = gallery(ImageGalleryMode.Pick(ImagePickTarget.ProjectCover))
		setGallery(fake)

		compose.onNodeWithTag(imageGalleryItemTag(8)).performTouchInput { doubleClick() }
		compose.waitForIdle()

		assertEquals(listOf(8), fake.picked)
	}

	@Test
	fun `The detail image fits above its actions in a narrow gallery`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake, width = 560.dp, height = 900.dp)

		compose.onNodeWithTag(imageGalleryItemTag(3)).performClick()
		passDoubleClickWindow()

		val image = compose.onNodeWithTag(IMAGE_GALLERY_DETAIL_IMAGE_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		val view = compose.onNodeWithTag(IMAGE_GALLERY_VIEW_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		assertTrue(image.height <= 240.dp, "image height ${image.height}")
		assertTrue(image.bottom <= view.top, "image bottom ${image.bottom} runs under the actions at ${view.top}")
	}

	@Test
	fun `Browsing shows the selected image without USE`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake)

		compose.onNodeWithTag(IMAGE_GALLERY_DETAIL_TAG).assertDoesNotExist()
		compose.onNodeWithTag(imageGalleryItemTag(3)).performClick()
		passDoubleClickWindow()

		compose.onNodeWithTag(IMAGE_GALLERY_DETAIL_TAG).assertIsDisplayed()
		compose.onNodeWithTag(IMAGE_GALLERY_USE_TAG).assertDoesNotExist()
	}

	@Test
	fun `The delete dialog lists what uses the image`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake)

		compose.onNodeWithTag(imageGalleryItemTag(3)).performClick()
		passDoubleClickWindow()
		compose.onNodeWithTag(IMAGE_GALLERY_DELETE_TAG).performClick()
		compose.waitForIdle()

		compose.onNodeWithText("This image is used in 3 places", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Encyclopedia entries (2): Alice, The Point", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Project cover (1): Alice in Wonderland", substring = true).assertIsDisplayed()
	}

	@Test
	fun `The delete dialog says when nothing uses the image`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake)

		compose.onNodeWithTag(imageGalleryItemTag(5)).performClick()
		passDoubleClickWindow()
		compose.onNodeWithTag(IMAGE_GALLERY_DELETE_TAG).performClick()
		compose.waitForIdle()

		compose.onNodeWithText("Nothing in the project uses this image.").assertIsDisplayed()
	}

	@Test
	fun `A project without images shows the empty state`() {
		setGallery(gallery(ImageGalleryMode.Browse, images = emptyList()))

		compose.onNodeWithTag(IMAGE_GALLERY_EMPTY_TAG).assertIsDisplayed()
		compose.onNodeWithTag(IMAGE_GALLERY_SEARCH_TAG).assertDoesNotExist()
		compose.onNodeWithTag(IMAGE_GALLERY_SORT_TAG).assertDoesNotExist()
	}

	@Test
	fun `Images are shown newest first`() {
		setGallery(gallery(ImageGalleryMode.Browse, images = previewGalleryImages().shuffled()))

		compose.onNodeWithText("↓ NEWEST").assertIsDisplayed()
		assertShownInOrder(13, 8, 5, 3)
	}

	@Test
	fun `The sort toggle flips the order back and forth`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake)

		compose.onNodeWithTag(IMAGE_GALLERY_SORT_TAG).performClick()
		compose.waitForIdle()
		assertEquals(false, fake.state.value.newestFirst)
		compose.onNodeWithText("↑ OLDEST").assertIsDisplayed()
		assertShownInOrder(3, 5, 8, 13)

		compose.onNodeWithTag(IMAGE_GALLERY_SORT_TAG).performClick()
		compose.waitForIdle()
		assertEquals(true, fake.state.value.newestFirst)
		assertShownInOrder(13, 8, 5, 3)
	}

	@Test
	fun `Typing in the search filters the grid by name, ignoring case`() {
		val fake = gallery(ImageGalleryMode.Browse)
		setGallery(fake)

		compose.onNodeWithTag(IMAGE_GALLERY_SEARCH_TAG).performTextInput("OF THE")
		compose.waitForIdle()

		assertEquals("OF THE", fake.state.value.searchQuery)
		compose.onNodeWithTag(imageGalleryItemTag(8)).assertIsDisplayed()
		listOf(3, 5, 13).forEach { compose.onNodeWithTag(imageGalleryItemTag(it)).assertDoesNotExist() }

		compose.onNodeWithTag(IMAGE_GALLERY_SEARCH_TAG).performTextReplacement("h")
		compose.waitForIdle()
		assertShownInOrder(8, 5, 3)
		compose.onNodeWithTag(imageGalleryItemTag(13)).assertDoesNotExist()
	}

	@Test
	fun `A search that matches nothing says so`() {
		setGallery(gallery(ImageGalleryMode.Browse))

		compose.onNodeWithTag(IMAGE_GALLERY_SEARCH_TAG).performTextInput("kraken")
		compose.waitForIdle()

		compose.onNodeWithText("No images match “kraken”.").assertIsDisplayed()
	}

	@Test
	fun `Pick mode can search and sort too`() {
		val fake = gallery(ImageGalleryMode.Pick(ImagePickTarget.ProjectCover))
		setGallery(fake)

		compose.onNodeWithTag(IMAGE_GALLERY_SEARCH_TAG).performTextInput("R")
		compose.onNodeWithTag(IMAGE_GALLERY_SORT_TAG).performClick()
		compose.waitForIdle()

		assertShownInOrder(3, 13)
		listOf(5, 8).forEach { compose.onNodeWithTag(imageGalleryItemTag(it)).assertDoesNotExist() }
		compose.onNodeWithTag(imageGalleryItemTag(13)).performTouchInput { doubleClick() }
		compose.waitForIdle()
		assertEquals(listOf(13), fake.picked)
	}

	private companion object {
		const val DOUBLE_CLICK_WINDOW_MS = 1_000L
	}
}
