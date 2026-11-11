package data.export

import com.conamobile.pdfkmp.image.readImageInfo
import com.darkrockstudios.apps.hammer.common.data.export.RasterSize
import com.darkrockstudios.apps.hammer.common.data.export.readRasterSize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** readRasterSize replaced pdfkmp's readImageInfo outside PDF export, so the two must agree on real images. */
class RasterSizeAgreementTest {

	private fun pdfkmpSize(bytes: ByteArray): RasterSize? = readImageInfo(bytes)?.let { RasterSize(it.widthPx, it.heightPx) }

	@Test
	fun `agrees with pdfkmp on encoded pngs and jpegs`() {
		val sizes = listOf(1 to 1, 40 to 20, 20 to 40, 333 to 7, 1024 to 768)
		for ((width, height) in sizes) {
			for (format in listOf("png", "jpg")) {
				val image = encoded(format, width, height)
				assertEquals(RasterSize(width, height), readRasterSize(image), "$format $width x $height")
				assertEquals(pdfkmpSize(image), readRasterSize(image), "$format $width x $height")
			}
		}
	}

	@Test
	fun `agrees with pdfkmp on a jpeg with an EXIF segment`() {
		val jpeg = withExifOrientation(encoded("jpg", 30, 10), orientation = 6)
		assertEquals(pdfkmpSize(jpeg), readRasterSize(jpeg))
		assertEquals(RasterSize(30, 10), readRasterSize(jpeg))
	}

	@Test
	fun `agrees with pdfkmp on a webp`() {
		assertEquals(pdfkmpSize(webp()), readRasterSize(webp()))
	}
}
