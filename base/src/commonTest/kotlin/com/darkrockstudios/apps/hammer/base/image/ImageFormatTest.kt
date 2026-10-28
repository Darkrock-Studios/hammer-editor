package com.darkrockstudios.apps.hammer.base.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageFormatTest {
	private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

	private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
	private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00)
	private val webp = "RIFF".encodeToByteArray() + bytes(0x10, 0x00, 0x00, 0x00) + "WEBPVP8 ".encodeToByteArray()
	private val gif = "GIF89a".encodeToByteArray() + bytes(0x01, 0x00)

	@Test
	fun `Recognizes each raster format`() {
		assertTrue(ImageFormat.sniff(png, "png"))
		assertTrue(ImageFormat.sniff(jpeg, "jpg"))
		assertTrue(ImageFormat.sniff(jpeg, "jpeg"))
		assertTrue(ImageFormat.sniff(webp, "webp"))
		assertTrue(ImageFormat.sniff(gif, "gif"))
		assertTrue(ImageFormat.sniff("GIF87a".encodeToByteArray(), "gif"))
	}

	@Test
	fun `Extension match is case insensitive`() {
		assertTrue(ImageFormat.sniff(png, "PNG"))
		assertTrue(ImageFormat.isAllowedExtension("JpEg"))
	}

	@Test
	fun `Rejects bytes that do not match the extension`() {
		assertFalse(ImageFormat.sniff(png, "jpg"))
		assertFalse(ImageFormat.sniff(jpeg, "png"))
		assertFalse(ImageFormat.sniff(gif, "webp"))
		assertFalse(ImageFormat.sniff("RIFF0000WAVE".encodeToByteArray(), "webp"))
		assertFalse(ImageFormat.sniff(png, "svg"))
	}

	@Test
	fun `Rejects truncated and empty input`() {
		assertFalse(ImageFormat.sniff(ByteArray(0), "png"))
		assertFalse(ImageFormat.sniff(png.copyOf(4), "png"))
		assertFalse(ImageFormat.sniff("RIFF".encodeToByteArray(), "webp"))
		assertFalse(ImageFormat.sniff(ByteArray(0), "svg"))
	}

	@Test
	fun `Rejects unknown extensions`() {
		assertFalse(ImageFormat.sniff(png, "bmp"))
		assertFalse(ImageFormat.sniff(png, ""))
		assertFalse(ImageFormat.isAllowedExtension("bmp"))
		assertFalse(ImageFormat.isAllowedExtension("exe"))
	}

	@Test
	fun `Recognizes SVG documents`() {
		assertTrue(ImageFormat.sniff("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray(), "svg"))
		assertTrue(ImageFormat.sniff("<?xml version=\"1.0\"?><svg/>".encodeToByteArray(), "svg"))
		assertTrue(ImageFormat.sniff("\n\t  <svg/>".encodeToByteArray(), "svg"))
		assertTrue(ImageFormat.sniff("\uFEFF<?xml version=\"1.0\"?>\n<svg/>".encodeToByteArray(), "svg"))
		assertTrue(ImageFormat.sniff("\uFEFF  \n<svg/>".encodeToByteArray(), "svg"))
	}

	@Test
	fun `Recognizes SVG after a full prolog`() {
		val svg = """
			<?xml version="1.0" encoding="UTF-8"?>
			<!-- Generator: an editor -->
			<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
			<svg xmlns="http://www.w3.org/2000/svg"/>
		""".trimIndent()
		assertTrue(ImageFormat.sniff(svg.encodeToByteArray(), "svg"))
		val subset = "<!DOCTYPE svg [ <!ENTITY a \"<b>\"> ]><svg/>"
		assertTrue(ImageFormat.sniff(subset.encodeToByteArray(), "svg"))
	}

	@Test
	fun `Rejects text that is not SVG`() {
		assertFalse(ImageFormat.sniff("<html><svg/></html>".encodeToByteArray(), "svg"))
		assertFalse(ImageFormat.sniff("hello <svg/>".encodeToByteArray(), "svg"))
	}

	@Test
	fun `Rejects XML that is not SVG`() {
		assertFalse(ImageFormat.sniff("<?xml version=\"1.0\"?><html/>".encodeToByteArray(), "svg"))
		assertFalse(ImageFormat.sniff("\uFEFF<?xml version=\"1.0\"?>".encodeToByteArray(), "svg"))
		assertFalse(ImageFormat.sniff("<?xml version=\"1.0\"?><!-- <svg/> --><plist/>".encodeToByteArray(), "svg"))
		assertFalse(ImageFormat.sniff("<?xml version=\"1.0\"".encodeToByteArray(), "svg"))
		assertFalse(ImageFormat.sniff("<!-- unterminated <svg/>".encodeToByteArray(), "svg"))
	}

	@Test
	fun `Valid names`() {
		assertTrue(ImageFormat.isValidName("Castle Map"))
		assertTrue(ImageFormat.isValidName("What? / Why: <A|B>"))
		assertTrue(ImageFormat.isValidName("Final.Map"))
	}

	@Test
	fun `Rejects names that do not survive the filename round trip`() {
		assertFalse(ImageFormat.isValidName(""))
		assertFalse(ImageFormat.isValidName("trailing."))
		assertFalse(ImageFormat.isValidName("trailing "))
		assertFalse(ImageFormat.isValidName("tilde~name"))
		assertFalse(ImageFormat.isValidName("lookalike\u2044slash"))
		// U+A789 is a letter, so the name validator admits it, but it decodes from a filename as ':'.
		assertFalse(ImageFormat.isValidName("Map\uA789North"))
	}

	@Test
	fun `Validate reports the first problem`() {
		assertNull(ImageFormat.validate("Map", "png", png))
		assertNull(ImageFormat.validate("Map", "PNG", png))
		assertEquals(ImageProblem.InvalidName, ImageFormat.validate("Map\uA789North", "png", png))
		assertEquals(ImageProblem.InvalidName, ImageFormat.validate("bad~name", "bmp", png))
		assertEquals(ImageProblem.UnsupportedType, ImageFormat.validate("Map", "bmp", png))
		val tooBig = png + ByteArray(ImageFormat.MAX_IMAGE_BYTES.toInt())
		assertEquals(ImageProblem.TooLarge, ImageFormat.validate("Map", "png", tooBig))
		assertEquals(ImageProblem.ContentMismatch, ImageFormat.validate("Map", "jpg", png))
		assertEquals(ImageProblem.ContentMismatch, ImageFormat.validate("Map", "svg", "<?xml version=\"1.0\"?><a/>".encodeToByteArray()))
	}

	@Test
	fun `Decodes url safe base64`() {
		assertContentEquals(byteArrayOf(-5, -1), ImageFormat.decodeBase64("-_8"))
	}

	@Test
	fun `Every allowed extension has a media type`() {
		for (ext in ImageFormat.ALLOWED_EXTENSIONS) {
			assertTrue(ImageFormat.mimeType(ext)?.startsWith("image/") == true, ext)
		}
		assertEquals("image/jpeg", ImageFormat.mimeType("JPG"))
		assertEquals("image/svg+xml", ImageFormat.mimeType("svg"))
		assertNull(ImageFormat.mimeType("bmp"))
		assertNull(ImageFormat.mimeType(""))
	}

	@Test
	fun `Allowed extensions and cap`() {
		for (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg")) {
			assertTrue(ImageFormat.isAllowedExtension(ext), ext)
		}
		assertEquals(8L * 1024 * 1024, ImageFormat.MAX_IMAGE_BYTES)
	}
}
