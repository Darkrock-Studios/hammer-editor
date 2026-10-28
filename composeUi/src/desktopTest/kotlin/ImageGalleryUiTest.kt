import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.images.ImageGallery
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_DELETE_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_DETAIL_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_EMPTY_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_GALLERY_USE_TAG
import com.darkrockstudios.apps.hammer.common.images.ImageGalleryUi
import com.darkrockstudios.apps.hammer.common.images.imageGalleryItemTag
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.images.FakeImageGallery
import com.darkrockstudios.apps.hammer.common.preview.images.previewGalleryImages
import com.darkrockstudios.apps.hammer.common.preview.images.previewImageUsages
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ImageGalleryUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun setGallery(gallery: FakeImageGallery) {
		compose.setContent {
			KoinApplicationPreview {
				ImageGalleryUi(gallery, modifier = Modifier.size(width = 1000.dp, height = 800.dp))
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

		compose.onNodeWithText("This image is used in 2 places", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Encyclopedia entries (2): Alice, The Point", substring = true).assertIsDisplayed()
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
	}

	private companion object {
		const val DOUBLE_CLICK_WINDOW_MS = 1_000L
	}
}
