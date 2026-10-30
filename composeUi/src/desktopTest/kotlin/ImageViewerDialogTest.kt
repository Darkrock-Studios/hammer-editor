import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import com.darkrockstudios.apps.hammer.common.images.IMAGE_VIEWER_CLOSE_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_VIEWER_COUNTER_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_VIEWER_LABEL_TAG
import com.darkrockstudios.apps.hammer.common.images.IMAGE_VIEWER_TAG
import com.darkrockstudios.apps.hammer.common.images.ImageViewerDialog
import com.darkrockstudios.apps.hammer.common.images.ImageViewerItem
import com.darkrockstudios.apps.hammer.common.images.imageViewerPageTag
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ImageViewerDialogTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val items = listOf("Harbour", "Lighthouse", "Storm").mapIndexed { index, name ->
		ImageViewerItem.ofFile(path = "/missing/$index.png", version = "h$index", label = name)
	}

	private var dismissed = 0

	private fun setViewer(items: List<ImageViewerItem>, initialIndex: Int) {
		compose.setContent {
			KoinApplicationPreview {
				ImageViewerDialog(items = items, initialIndex = initialIndex, onDismissed = { dismissed++ })
			}
		}
	}

	@Test
	fun `The viewer opens on the initial image`() {
		setViewer(items, initialIndex = 1)

		compose.onNodeWithTag(imageViewerPageTag(1)).assertIsDisplayed()
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")
		compose.onNodeWithTag(IMAGE_VIEWER_LABEL_TAG).assertTextEquals("Lighthouse")
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `The arrow keys page through the images`() {
		setViewer(items, initialIndex = 0)

		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
		compose.waitForIdle()
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")

		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
		compose.waitForIdle()
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("1 / 3")
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `The mouse wheel pages through the images`() {
		setViewer(items, initialIndex = 0)

		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performMouseInput { scroll(1f) }
		compose.waitForIdle()
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")

		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performMouseInput { scroll(-1f) }
		compose.waitForIdle()
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("1 / 3")
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `Dragging with the mouse pages to the next image`() {
		setViewer(items, initialIndex = 0)

		compose.onNodeWithTag(IMAGE_VIEWER_TAG).performMouseInput {
			press()
			repeat(10) { moveBy(Offset(-20f, 0f), delayMillis = 16) }
			release()
		}
		compose.waitForIdle()

		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")
	}

	@Test
	fun `A single image has no counter`() {
		setViewer(items.take(1), initialIndex = 0)

		compose.onNodeWithTag(IMAGE_VIEWER_LABEL_TAG).assertTextEquals("Harbour")
		compose.onNodeWithTag(IMAGE_VIEWER_COUNTER_TAG).assertDoesNotExist()
	}

	@Test
	fun `Close dismisses the viewer after it animates out`() {
		setViewer(items, initialIndex = 2)
		assertEquals(0, dismissed)

		compose.onNodeWithTag(IMAGE_VIEWER_CLOSE_TAG).performClick()
		compose.waitForIdle()

		assertEquals(1, dismissed)
	}
}
