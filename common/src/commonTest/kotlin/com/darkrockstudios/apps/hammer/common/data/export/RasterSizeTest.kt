package com.darkrockstudios.apps.hammer.common.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RasterSizeTest {

	private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

	private fun u16(value: Int): ByteArray = bytes(value shr 8, value)

	private fun u32(value: Long): ByteArray = u16((value shr 16).toInt()) + u16(value.toInt())

	private fun png(width: Long, height: Long): ByteArray =
		bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
			u32(13) + "IHDR".encodeToByteArray() + u32(width) + u32(height) + bytes(8, 2, 0, 0, 0)

	private val soi = bytes(0xFF, 0xD8)

	private fun segment(marker: Int, payload: ByteArray): ByteArray = bytes(0xFF, marker) + u16(payload.size + 2) + payload

	private fun sof(marker: Int, width: Int, height: Int): ByteArray =
		segment(marker, bytes(8) + u16(height) + u16(width) + bytes(3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1))

	private val app0 = segment(0xE0, "JFIF".encodeToByteArray() + bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))

	@Test
	fun `a png gives the size its IHDR states`() {
		assertEquals(RasterSize(640, 480), readRasterSize(png(640, 480)))
	}

	@Test
	fun `a png with a zero side, a side past Int MAX_VALUE, or a cut header gives null`() {
		assertNull(readRasterSize(png(0, 480)))
		assertNull(readRasterSize(png(0x8000_0000, 480)))
		assertNull(readRasterSize(png(640, 480).copyOf(23)))
	}

	@Test
	fun `a jpeg gives the size of its first frame`() {
		assertEquals(RasterSize(300, 200), readRasterSize(soi + app0 + sof(0xC0, 300, 200)))
		assertEquals(RasterSize(300, 200), readRasterSize(soi + app0 + sof(0xC2, 300, 200)))
	}

	@Test
	fun `huffman, arithmetic-coding and JPG segments are not frames`() {
		val notFrames = listOf(0xC4, 0xC8, 0xCC).fold(ByteArray(0)) { acc, marker -> acc + segment(marker, bytes(0, 0, 0)) }
		assertEquals(RasterSize(16, 9), readRasterSize(soi + notFrames + sof(0xC1, 16, 9)))
	}

	@Test
	fun `fill bytes and standalone markers before a frame are skipped`() {
		val standalone = bytes(0xFF, 0xD0, 0xFF, 0x01)
		val filled = bytes(0xFF, 0xFF, 0xFF) + sof(0xC0, 32, 24).copyOfRange(1, sof(0xC0, 32, 24).size)
		assertEquals(RasterSize(32, 24), readRasterSize(soi + standalone + filled))
	}

	@Test
	fun `a jpeg with no frame, a cut frame, a broken chain or a zero side gives null`() {
		assertNull(readRasterSize(soi + app0))
		assertNull(readRasterSize((soi + sof(0xC0, 300, 200)).copyOf(10)))
		assertNull(readRasterSize(soi + bytes(0x00, 0xC0) + sof(0xC0, 300, 200)))
		assertNull(readRasterSize(soi + segment(0xE0, ByteArray(0)).copyOf(3) + bytes(0x00) + sof(0xC0, 1, 1)))
		assertNull(readRasterSize(soi + sof(0xC0, 0, 200)))
	}

	@Test
	fun `anything else gives null`() {
		assertNull(readRasterSize(ByteArray(0)))
		assertNull(readRasterSize("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
		assertNull(readRasterSize("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()))
	}
}
