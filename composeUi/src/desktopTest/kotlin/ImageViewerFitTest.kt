import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.images.ImageViewerDialog
import com.darkrockstudios.apps.hammer.common.images.ImageViewerItem
import com.darkrockstudios.apps.hammer.common.images.imageViewerPageTag
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/** A loaded image opens fitted inside the viewer, whatever zoom the state held before. */
class ImageViewerFitTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun Color.isRed() = red > 0.8f && green < 0.3f && blue < 0.3f
	private fun Color.isBlue() = blue > 0.8f && red < 0.3f && green < 0.3f

	/** A red [width] by [height] image with a blue square in its top left corner. */
	private fun wideImageFile(width: Int, height: Int): File {
		val bitmap = ImageBitmap(width, height)
		Canvas(bitmap).apply {
			drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { color = Color.Red })
			drawRect(Rect(0f, 0f, 40f, 40f), Paint().apply { color = Color.Blue })
		}
		val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
		return File.createTempFile("viewer-fit", ".png").apply {
			deleteOnExit()
			writeBytes(png)
		}
	}

	@Test
	fun `a loaded image opens fitted inside the viewer`() = assertOpensFitted(wideImageFile(width = 400, height = 200))

	@Test
	fun `a small image is enlarged to fit, not past it`() = assertOpensFitted(wideImageFile(width = 100, height = 50))

	private fun assertOpensFitted(file: File) {
		compose.setContent {
			KoinApplicationPreview {
				Box(Modifier.size(width = 1000.dp, height = 800.dp)) {
					ImageViewerDialog(
						items = listOf(ImageViewerItem.ofFile(path = file.path, version = "v1", label = "Wide")),
						initialIndex = 0,
						onDismissed = {},
					)
				}
			}
		}
		val page = compose.onNodeWithTag(imageViewerPageTag(0))
		compose.waitUntil(timeoutMillis = 15_000) {
			val pixels = page.captureToImage().toPixelMap()
			pixels[pixels.width / 2, pixels.height / 2].isRed()
		}

		val pixels = page.captureToImage().toPixelMap()
		val w = pixels.width
		val h = pixels.height
		// Fitted, a 2:1 image is bounded by the page's width and letterboxed above and below.
		val drawnHeight = w / 2
		val top = (h - drawnHeight) / 2
		assertTrue(!pixels[w / 2, top / 2].isRed(), "the letterbox above the image is not the image")
		assertTrue(pixels[10, top + 10].isBlue(), "the image's top left corner shows in the page's top left")
		assertTrue(pixels[w - 10, top + 10].isRed(), "the image's top right corner shows in the page's top right")
	}
}
