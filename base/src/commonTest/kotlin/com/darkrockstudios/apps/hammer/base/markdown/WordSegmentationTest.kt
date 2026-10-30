package com.darkrockstudios.apps.hammer.base.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class WordSegmentationTest {

	private fun boundaries(text: String) = wordBoundaries(text).toList()

	@Test
	fun `empty text has only the start boundary`() {
		assertEquals(listOf(0), boundaries(""))
		assertEquals(emptyList(), wordSpans("").toList())
		assertEquals(emptyList(), words(""))
	}

	@Test
	fun `boundaries include both ends`() {
		assertEquals(listOf(0, 2, 3, 5), boundaries("ab cd"))
		assertEquals(listOf(0, 1), boundaries("a"))
	}

	@Test
	fun `spans skip segments without a letter or digit`() {
		assertEquals(listOf(0..1, 4..8), wordSpans("Hi, there!").toList())
		assertEquals(listOf("Hi", "there"), words("Hi, there!"))
	}

	@Test
	fun `sequences can be iterated more than once`() {
		val spans = wordSpans("one two")
		assertEquals(spans.toList(), spans.toList())
	}

	@Test
	fun `a surrogate pair is one code point`() {
		val gothic = "𐌰𐌱 x"
		assertEquals(listOf(0, 4, 5, 6), boundaries(gothic))
		assertEquals(listOf("𐌰𐌱", "x"), words(gothic))
		assertEquals(listOf(0, 2, 4), boundaries("𠀀𠀁"))
	}

	@Test
	fun `a lone surrogate is a code point of class Other`() {
		assertEquals(listOf(0, 1, 2, 3), boundaries("a\uD800b"))
		assertEquals(listOf("a", "b"), words("a\uD800b"))
		assertEquals(listOf(0, 1, 2), boundaries("\uDC00a"))
		assertEquals(listOf(0, 1, 2), boundaries("a\uD800"))
	}

	@Test
	fun `CR LF stays together and breaks around it`() {
		assertEquals(listOf(0, 1, 3, 4), boundaries("a\r\nb"))
		assertEquals(listOf(0, 1, 2), boundaries("\n\n"))
	}

	@Test
	fun `ZWJ joins an emoji sequence`() {
		val family = "👩‍👩‍👧"
		assertEquals(listOf(0, family.length), boundaries(family))
		assertEquals(emptyList(), words(family))
	}

	@Test
	fun `horizontal spaces stay together`() {
		assertEquals(listOf(0, 1, 3, 4), boundaries("a  b"))
	}

	@Test
	fun `extend and format characters attach to what precedes them`() {
		assertEquals(listOf("café"), words("café"))
		assertEquals(listOf("co­op"), words("co­op"))
		assertEquals(listOf(0, 1, 2, 3), boundaries("\ńa"))
	}

	@Test
	fun `letters around mid letter punctuation`() {
		assertEquals(listOf("can't"), words("can't"))
		assertEquals(listOf("can"), words("can'"))
		assertEquals(listOf("e.g"), words("e.g."))
		assertEquals(listOf("a", "b"), words("a''b"))
	}

	@Test
	fun `hebrew quotes`() {
		assertEquals(listOf("א'"), words("א'"))
		assertEquals(listOf("א\"ב"), words("א\"ב"))
		assertEquals(listOf("א", "ב"), words("א\"\"ב"))
	}

	@Test
	fun `numbers and their separators`() {
		assertEquals(listOf("3.14"), words("3.14"))
		assertEquals(listOf("1,000"), words("1,000"))
		assertEquals(listOf("3"), words("3."))
		assertEquals(listOf("abc123"), words("abc123"))
		assertEquals(listOf("3", "30"), words("3:30"))
	}

	@Test
	fun `katakana runs and extenders`() {
		assertEquals(listOf("コーヒー"), words("コーヒー"))
		assertEquals(listOf("a_1", "_b"), words("a_1 _b"))
		assertEquals(emptyList(), words("__"))
	}

	@Test
	fun `regional indicators pair up`() {
		val flags = "🇺🇸🇫🇷"
		assertEquals(listOf(0, 4, 8), boundaries(flags))
		assertEquals(listOf(0, 4, 6), boundaries(flags.substring(0, 6)))
		assertEquals(listOf(0, 5, 9), boundaries("🇺́🇸🇫🇷"))
	}

	@Test
	fun `ideographs and Thai letters are words one by one`() {
		assertEquals(listOf("日", "本", "語", "text"), words("日本語text"))
		assertEquals(listOf("ไ", "ท", "ย"), words("ไทย"))
	}

	@Test
	fun `a long manuscript segments quickly`() {
		val chunk = "The quick brown fox's 3.14 jumps, over 日本語 the lazy dog.\n"
		val repeats = 200_000 / chunk.length + 1
		val text = chunk.repeat(repeats)
		val perChunk = wordSpans(chunk).count()
		var count = 0
		val elapsed = measureTime { count = wordSpans(text).count() }
		assertEquals(perChunk * repeats, count)
		assertTrue(elapsed < 1.seconds, "Took $elapsed")
	}
}
