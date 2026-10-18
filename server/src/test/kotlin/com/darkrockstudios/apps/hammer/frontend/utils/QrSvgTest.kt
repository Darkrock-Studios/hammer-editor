package com.darkrockstudios.apps.hammer.frontend.utils

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrSvgTest {

	private val uri = "otpauth://totp/Hammer%20(write.example.com):author%40example.com" +
		"?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Hammer%20(write.example.com)"

	@Test
	fun `the svg decodes back to the encoded text`() {
		val svg = qrCodeSvg(uri)

		assertEquals(uri, decode(svg))
	}

	@Test
	fun `the svg is self-contained markup with no script or external reference`() {
		val svg = qrCodeSvg(uri)

		assertTrue(svg.startsWith("<svg"))
		assertFalse(svg.contains("<script"))
		assertFalse(svg.contains("href"))
	}

	/** Rasterises the svg's rects at a few pixels per module and reads it with zxing's own reader. */
	private fun decode(svg: String): String {
		val viewBox = Regex("""viewBox="0 0 (\d+) (\d+)"""").find(svg)!!.groupValues
		val modules = viewBox[1].toInt()
		val scale = 4
		val size = modules * scale
		val pixels = IntArray(size * size) { WHITE }
		Regex("""<rect x="(\d+)" y="(\d+)" width="(\d+)" height="1"/>""").findAll(svg).forEach { rect ->
			val (x, y, width) = rect.destructured
			for (mx in x.toInt() until x.toInt() + width.toInt()) {
				for (px in mx * scale until (mx + 1) * scale) {
					for (py in y.toInt() * scale until (y.toInt() + 1) * scale) {
						pixels[py * size + px] = BLACK
					}
				}
			}
		}
		val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
		return QRCodeReader().decode(bitmap).text
	}

	private companion object {
		const val WHITE = 0xFFFFFF
		const val BLACK = 0x000000
	}
}
