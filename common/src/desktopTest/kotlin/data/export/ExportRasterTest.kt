package data.export

import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportRaster
import com.darkrockstudios.apps.hammer.common.data.export.MAX_RASTER_SIDE
import com.darkrockstudios.apps.hammer.common.data.export.exportRaster
import org.junit.jupiter.api.Test
import java.util.zip.CRC32
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExportRasterTest {

	private fun raster(extension: String, bytes: ByteArray): ExportRaster? =
		exportRaster(ExportImage(1, "Cat", extension, bytes))

	@Test
	fun `a png and an upright jpeg keep their own bytes`() {
		val png = encoded("png")
		val jpeg = withExifOrientation(encoded("jpg"), orientation = 1)

		val fromPng = assertNotNull(raster("PNG", png))
		assertSame(png, fromPng.bytes)
		assertEquals(ExportRaster.Format.Png, fromPng.format)
		assertEquals(40 to 20, fromPng.widthPx to fromPng.heightPx)

		val fromJpeg = assertNotNull(raster("jpeg", jpeg))
		assertSame(jpeg, fromJpeg.bytes)
		assertEquals(ExportRaster.Format.Jpeg, fromJpeg.format)
		assertEquals(0.5f, fromJpeg.aspectRatio, 0.001f)
	}

	@Test
	fun `a webp becomes a png`() {
		val raster = assertNotNull(raster("webp", webp(width = 30, height = 60)))

		assertEquals(ExportRaster.Format.Png, raster.format)
		assertEquals(30 to 60, raster.widthPx to raster.heightPx)
	}

	@Test
	fun `a jpeg turned by its exif orientation becomes an upright png`() {
		val turned = withExifOrientation(encoded("jpg", width = 40, height = 20), orientation = 6)

		val raster = assertNotNull(raster("jpg", turned))

		assertEquals(ExportRaster.Format.Png, raster.format)
		assertEquals(20 to 40, raster.widthPx to raster.heightPx)
	}

	@Test
	fun `the format follows the bytes rather than the extension`() {
		assertEquals(ExportRaster.Format.Jpeg, assertNotNull(raster("png", encoded("jpg"))).format)
	}

	@Test
	fun `an svg, an unknown extension, or an unreadable header gives no raster`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		assertNull(raster("svg", svg.encodeToByteArray()))
		assertNull(raster("bmp", encoded("bmp")))
		assertNull(raster("png", encoded("png").copyOf(10)))
		assertNull(raster("webp", "not an image".encodeToByteArray()))
	}

	@Test
	fun `a header that gives a zero size gives no raster`() {
		// The IHDR width is the four bytes after the signature, the chunk length and its type.
		val zeroWidth = encoded("png").also { it.fill(0, fromIndex = 16, toIndex = 20) }

		assertNull(raster("png", zeroWidth))
	}

	@Test
	fun `a header that claims a side over the jpeg limit gives no raster`() {
		assertNull(raster("png", pngHeader(width = 0x10000000, height = 20)))
		assertNull(raster("png", pngHeader(width = 40, height = MAX_RASTER_SIDE + 1)))
		assertEquals(MAX_RASTER_SIDE, assertNotNull(raster("png", pngHeader(MAX_RASTER_SIDE, 20))).widthPx)
	}

	/** A PNG signature and IHDR chunk claiming [width] by [height], with no image data. */
	private fun pngHeader(width: Int, height: Int): ByteArray {
		fun u32(value: Int) = ByteArray(4) { (value ushr (24 - it * 8)).toByte() }
		val signature = byteArrayOf(0x89.toByte()) + "PNG".encodeToByteArray() + byteArrayOf(13, 10, 26, 10)
		// Bit depth 8, truecolour, then the default compression, filter and interlace methods.
		val ihdr = "IHDR".encodeToByteArray() + u32(width) + u32(height) + byteArrayOf(8, 2, 0, 0, 0)
		val crc = CRC32().apply { update(ihdr) }.value.toInt()
		return signature + u32(13) + ihdr + u32(crc)
	}
}
