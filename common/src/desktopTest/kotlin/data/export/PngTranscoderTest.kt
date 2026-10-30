package data.export

import com.conamobile.pdfkmp.image.readImageInfo
import com.darkrockstudios.apps.hammer.common.data.export.jpegExifOrientation
import com.darkrockstudios.apps.hammer.common.data.export.transcodeToPng
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PngTranscoderTest {

	/** A [width] by [height] JPEG, red in its left half and blue in its right. */
	private fun jpeg(width: Int, height: Int): ByteArray {
		val surface = Surface.makeRasterN32Premul(width, height)
		surface.canvas.clear(Color.BLUE)
		Paint().use { paint ->
			paint.color = Color.RED
			surface.canvas.drawRect(Rect.makeWH(width / 2f, height.toFloat()), paint)
		}
		return checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG)).bytes
	}

	private fun pixels(png: ByteArray): Bitmap = Image.makeFromEncoded(png).use { image ->
		Bitmap().also {
			it.allocN32Pixels(image.width, image.height)
			image.readPixels(it)
		}
	}

	@Test
	fun `a jpeg in orientation 6 comes out upright with its edges swapped`() {
		val rotated = withExifOrientation(jpeg(width = 40, height = 20), orientation = 6)
		assertEquals(6, jpegExifOrientation(rotated))

		val png = assertNotNull(transcodeToPng(rotated, orientation = 6))

		val info = assertNotNull(readImageInfo(png))
		assertEquals(20 to 40, info.widthPx to info.heightPx)
		pixels(png).use { pixels ->
			// Turned a quarter clockwise, the stored left (red) half becomes the top.
			assertTrue(Color.getR(pixels.getColor(10, 5)) > 200, "top should be red")
			assertTrue(Color.getB(pixels.getColor(10, 35)) > 200, "bottom should be blue")
		}
	}

	@Test
	fun `a large image shrinks so its longer edge fits the bound`() {
		val png = assertNotNull(transcodeToPng(jpeg(width = 400, height = 100), maxEdge = 200))

		val info = assertNotNull(readImageInfo(png))
		assertEquals(200 to 50, info.widthPx to info.heightPx)
	}

	@Test
	fun `bytes that do not decode give no png`() {
		assertNull(transcodeToPng("not an image".encodeToByteArray()))
	}
}

/** [jpeg] with a big-endian EXIF APP1 segment carrying [orientation] spliced in after SOI. */
internal fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
	val tiff = byteArrayOf(
		'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
		0, 1,
		0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
		0, 0, 0, 0,
	)
	val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
	val length = payload.size + 2
	val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
	return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
}
