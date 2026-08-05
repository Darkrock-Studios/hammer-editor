package com.darkrockstudios.apps.hammer.common.util

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class CharBuffersTest {

	/** A fixed-size workspace, so a test can pin what happens when the sink runs out. */
	private class FixedBuffers(sinkSize: Int) : ScanBuffers {
		val chars = CharArray(sinkSize)
		private var bytes = ByteArray(0)
		override fun charBuffer(minCapacity: Int) = chars
		override fun byteBuffer(minCapacity: Int): ByteArray {
			if (bytes.size < minCapacity) bytes = ByteArray(minCapacity)
			return bytes
		}
	}

	private fun decode(text: String, sinkSize: Int = 256): String {
		val source = Buffer().writeUtf8(text)
		val byteCount = source.size.toInt()
		val buffers = FixedBuffers(sinkSize)
		val count = source.readUtf8Into(buffers, byteCount)
		return buffers.chars.concatToString(0, count)
	}

	private fun decodeBytes(bytes: ByteArray, byteCount: Int = bytes.size, sinkSize: Int = 16): String {
		val source = Buffer().write(bytes)
		val buffers = FixedBuffers(sinkSize)
		val count = source.readUtf8Into(buffers, byteCount)
		return buffers.chars.concatToString(0, count)
	}

	@Test
	fun `ascii decodes unchanged`() {
		assertEquals("The quick brown fox", decode("The quick brown fox"))
	}

	@Test
	fun `two byte sequences decode`() {
		assertEquals("café naïve", decode("café naïve"))
	}

	@Test
	fun `three byte sequences decode`() {
		assertEquals("日本語のテキスト", decode("日本語のテキスト"))
	}

	@Test
	fun `four byte sequences decode to a surrogate pair`() {
		val emoji = "😀"
		val decoded = decode("a${emoji}b")
		assertEquals("a${emoji}b", decoded)
		assertEquals(4, decoded.length)
	}

	@Test
	fun `an empty source decodes to nothing`() {
		assertEquals("", decode(""))
	}

	@Test
	fun `decoding stops when the sink is full`() {
		assertEquals("The q", decode("The quick brown fox", sinkSize = 5))
	}

	@Test
	fun `a surrogate pair is not split across the end of the sink`() {
		// Room for "a" plus one char: the pair needs two, so it is left out rather than half-written.
		assertEquals("a", decode("a😀", sinkSize = 2))
	}

	@Test
	fun `a truncated sequence decodes to the replacement character`() {
		assertEquals("a�", decodeBytes(byteArrayOf(0x61, 0xC3.toByte())))
	}

	@Test
	fun `a stray continuation byte decodes to the replacement character`() {
		assertEquals("a�b", decodeBytes(byteArrayOf(0x61, 0x80.toByte(), 0x62)))
	}

	@Test
	fun `reading fewer bytes than the source holds stops at the limit`() {
		val source = Buffer().writeUtf8("abcdef")
		val buffers = FixedBuffers(16)
		val count = source.readUtf8Into(buffers, 3)
		assertEquals("abc", buffers.chars.concatToString(0, count))
	}
}
