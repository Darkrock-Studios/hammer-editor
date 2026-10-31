package com.darkrockstudios.apps.hammer.common.data.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ExifOrientationTest {

	private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

	private fun u16(value: Int, bigEndian: Boolean): ByteArray =
		if (bigEndian) bytes(value shr 8, value) else bytes(value, value shr 8)

	private fun u32(value: Int, bigEndian: Boolean): ByteArray =
		if (bigEndian) u16(value shr 16, true) + u16(value, true) else u16(value, false) + u16(value shr 16, false)

	/** A TIFF block whose IFD0 holds a resolution entry, then the orientation entry. */
	private fun tiff(orientation: Int, bigEndian: Boolean): ByteArray {
		val order = if (bigEndian) "MM" else "II"
		val xResolution = u16(0x011A, bigEndian) + u16(5, bigEndian) + u32(1, bigEndian) + u32(0, bigEndian)
		val orientationEntry = u16(0x0112, bigEndian) + u16(3, bigEndian) + u32(1, bigEndian) +
			u16(orientation, bigEndian) + u16(0, bigEndian)
		return order.encodeToByteArray() + u16(42, bigEndian) + u32(8, bigEndian) +
			u16(2, bigEndian) + xResolution + orientationEntry + u32(0, bigEndian)
	}

	private fun segment(marker: Int, payload: ByteArray): ByteArray =
		bytes(0xFF, marker) + u16(payload.size + 2, bigEndian = true) + payload

	private fun app1(tiff: ByteArray): ByteArray = segment(0xE1, "Exif".encodeToByteArray() + bytes(0, 0) + tiff)

	private val soi = bytes(0xFF, 0xD8)
	private val app0 = segment(0xE0, "JFIF".encodeToByteArray() + bytes(0, 1, 2, 0, 0, 1, 0, 1, 0, 0))
	private val scan = segment(0xDA, bytes(1, 1, 0, 0, 63, 0)) + bytes(0x12, 0x34, 0xFF, 0xD9)

	private fun jpeg(vararg segments: ByteArray): ByteArray =
		segments.fold(soi) { acc, it -> acc + it } + scan

	@Test
	fun `a big-endian orientation is read`() {
		for (orientation in listOf(1, 6, 8)) {
			assertEquals(orientation, jpegExifOrientation(jpeg(app0, app1(tiff(orientation, bigEndian = true)))))
		}
	}

	@Test
	fun `a little-endian orientation is read`() {
		for (orientation in listOf(1, 6, 8)) {
			assertEquals(orientation, jpegExifOrientation(jpeg(app0, app1(tiff(orientation, bigEndian = false)))))
		}
	}

	@Test
	fun `an xmp segment before the exif one is skipped`() {
		val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/".encodeToByteArray() + bytes(0))

		assertEquals(6, jpegExifOrientation(jpeg(xmp, app1(tiff(6, bigEndian = true)))))
	}

	@Test
	fun `a jpeg without an app1 segment is upright`() {
		assertEquals(1, jpegExifOrientation(jpeg(app0)))
	}

	@Test
	fun `a truncated app1 segment is upright`() {
		val whole = jpeg(app1(tiff(6, bigEndian = true)))
		for (cut in 4 until whole.size - scan.size) {
			assertEquals(1, jpegExifOrientation(whole.copyOf(cut)), "cut at $cut")
		}
	}

	@Test
	fun `an app1 segment that ends inside its ifd is upright`() {
		val tiff = tiff(6, bigEndian = true)
		val short = tiff.copyOf(tiff.size - 10)

		assertEquals(1, jpegExifOrientation(jpeg(app1(short))))
	}

	@Test
	fun `an orientation outside the eight standard values is upright`() {
		assertEquals(1, jpegExifOrientation(jpeg(app1(tiff(9, bigEndian = true)))))
		assertEquals(1, jpegExifOrientation(jpeg(app1(tiff(0, bigEndian = false)))))
	}

	@Test
	fun `bytes that are not a jpeg are upright`() {
		assertEquals(1, jpegExifOrientation(ByteArray(0)))
		assertEquals(1, jpegExifOrientation(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
	}

	@Test
	fun `an ifd offset past the segment is upright`() {
		val tiff = tiff(6, bigEndian = true).also { u32(0x7FFFFFF0, true).copyInto(it, destinationOffset = 4) }

		assertEquals(1, jpegExifOrientation(jpeg(app1(tiff))))
	}
}
