import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.texteditor.richstyle.ImageBlockResource
import com.darkrockstudios.texteditor.richstyle.ImageProvider
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownEditFieldImageLoadTest {

	@get:Rule
	val compose = createComposeRule()

	/** Holds every image in [ImageBlockResource.Loading] until [deliver]. */
	private class LateImageProvider : ImageProvider {
		val state: MutableState<ImageBlockResource> = mutableStateOf(ImageBlockResource.Loading)
		override fun resolve(source: String): State<ImageBlockResource> = state

		fun deliver(bitmap: ImageBitmap) {
			state.value = ImageBlockResource.Loaded(bitmap)
		}
	}

	private val provider = LateImageProvider()
	private val cat = ImageDef(id = 3, name = "Cat", fileExtension = "png")

	private fun mount() {
		compose.setContent {
			KoinApplicationPreview {
				CompositionLocalProvider(LocalImageProvider provides provider) {
					Box(Modifier.size(width = 800.dp, height = 300.dp)) {
						MarkdownEditField(
							initialMarkdown = "Below",
							onMarkdownChanged = {},
							enableSpellChecking = false,
							testTag = EDITOR_TAG,
							imagePicker = { cat },
						)
					}
				}
			}
		}
	}

	/** Red pixels in the editor's last [rows] rows of pixels, all of them by default. */
	private fun redPixels(rows: Int? = null): Int {
		val pixels = compose.onNodeWithTag(EDITOR_TAG).captureToImage().toPixelMap()
		var count = 0
		for (y in pixels.height - (rows ?: pixels.height) until pixels.height) {
			for (x in 0 until pixels.width) {
				val c = pixels[x, y]
				if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) count++
			}
		}
		return count
	}

	@Test
	fun `text below an image that loads after it was inserted can be scrolled to`() {
		mount()
		compose.waitForIdle()

		compose.onNodeWithTag(MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG, useUnmergedTree = true).performClick()
		compose.waitForIdle()
		compose.runOnIdle { provider.deliver(tallRedBitmap()) }
		compose.waitForIdle()

		compose.onNodeWithTag(EDITOR_TAG).performClick()
		compose.onNodeWithTag(EDITOR_TAG).performKeyInput {
			withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveEnd) }
		}
		compose.waitForIdle()

		assertTrue(redPixels() > 0, "the image is drawn")
		// Scrolled to the end, the last line shows at the bottom, below the image.
		assertEquals(0, redPixels(rows = BOTTOM_ROWS))
	}

	private fun tallRedBitmap(): ImageBitmap {
		val bitmap = ImageBitmap(40, 3000)
		Canvas(bitmap).drawRect(Rect(0f, 0f, 40f, 3000f), Paint().apply { color = Color.Red })
		return bitmap
	}

	private companion object {
		const val EDITOR_TAG = "markdown-edit-field"
		const val BOTTOM_ROWS = 6
	}
}
