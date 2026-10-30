package com.darkrockstudios.apps.hammer.base.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class WordCountTest {

	@Test
	fun `empty string is zero`() {
		assertEquals(0, countWords(""))
	}

	@Test
	fun `whitespace only is zero`() {
		assertEquals(0, countWords("   "))
		assertEquals(0, countWords("\n\n\t"))
	}

	@Test
	fun `single word is one`() {
		assertEquals(1, countWords("hello"))
	}

	@Test
	fun `two words separated by space`() {
		assertEquals(2, countWords("hello world"))
	}

	@Test
	fun `leading and trailing whitespace does not change count`() {
		assertEquals(2, countWords("  hello   world  "))
	}

	@Test
	fun `newline counts as a separator`() {
		assertEquals(4, countWords("line one\nline two"))
	}

	@Test
	fun `CRLF counts as a single separator`() {
		assertEquals(2, countWords("hello\r\nworld"))
	}

	@Test
	fun `mixed whitespace runs collapse to single separator`() {
		assertEquals(3, countWords("one \t two\n\n three"))
	}

	@Test
	fun `a non-breaking space separates words`() {
		assertEquals(2, countWords("hello world"))
	}

	@Test
	fun `horizontal rule is not a word`() {
		assertEquals(2, countWords("one\n\n---\n\ntwo"))
		assertEquals(0, countWords("***"))
		assertEquals(0, countWords("___"))
	}

	@Test
	fun `heading marker is not a word`() {
		assertEquals(1, countWords("# Heading"))
	}

	@Test
	fun `bullet markers are not words`() {
		assertEquals(2, countWords("- a\n- b"))
	}

	@Test
	fun `blockquote marker is not a word`() {
		assertEquals(2, countWords("> quoted text"))
	}

	@Test
	fun `attached punctuation stays part of the word`() {
		assertEquals(1, countWords("**bold**"))
		assertEquals(1, countWords("don't"))
		assertEquals(1, countWords("end."))
	}

	@Test
	fun `an image counts for nothing`() {
		assertEquals(0, countWords("![Cat](hammer://image/3)"))
		assertEquals(0, countWords("![A black cat](hammer://image/3 \"On the mat\")"))
		assertEquals(2, countWords("Before\n\n![Black cat](hammer://image/3)\n\nafter"))
	}

	@Test
	fun `an image inside a line leaves the words around it`() {
		assertEquals(3, countWords("one ![two three](https://example.com/a(b).png) four five"))
		assertEquals(2, countWords("see ![a [nested] alt](<hammer://image/3>) here"))
		assertEquals(2, countWords("Figure: ![Figure 12, plate 4](hammer://image/42) above"))
	}

	@Test
	fun `an image ends the word before it`() {
		assertEquals(2, countWords("cat![Cat](hammer://image/3)s"))
	}

	@Test
	fun `text that only looks like an image still counts`() {
		assertEquals(2, countWords("\\![not](x)"))
		assertEquals(4, countWords("![open alt](no close"))
		assertEquals(5, countWords("![split\nalt](hammer://image/3)"))
	}

	@Test
	fun `a hyphen separates words`() {
		assertEquals(2, countWords("well-known"))
	}

	@Test
	fun `numbers keep their separators`() {
		assertEquals(1, countWords("3.14"))
		assertEquals(1, countWords("1,000"))
	}

	@Test
	fun `abbreviations and apostrophes stay whole`() {
		assertEquals(1, countWords("U.S.A."))
		assertEquals(1, countWords("rock’n’roll"))
	}

	@Test
	fun `underscores join words but are not words`() {
		assertEquals(1, countWords("hello_world"))
		assertEquals(0, countWords("___"))
	}

	@Test
	fun `a katakana run is one word`() {
		assertEquals(1, countWords("コーヒー"))
	}

	@Test
	fun `each ideograph is a word`() {
		assertEquals(3, countWords("日本語"))
	}

	@Test
	fun `each Thai letter is a word without a dictionary`() {
		assertEquals(7, countWords("ภาษาไทย"))
	}

	@Test
	fun `emoji are not words`() {
		assertEquals(0, countWords("🐈 ❤️ 🇺🇸"))
	}

	@Test
	fun `a lone combining mark is not a word`() {
		assertEquals(1, countWords("a \u0301"))
	}

	@Test
	fun `counted word spans of empty text are empty`() {
		assertEquals(emptyList<IntRange>(), countedWordSpans("").toList())
	}

	@Test
	fun `counted word spans index the original string around an image`() {
		val text = "cat![Cat](hammer://image/3)s and ![dog](x) more"
		val spans = countedWordSpans(text).toList()
		assertEquals(listOf(0..2, 27..27, 29..31, 43..46), spans)
		assertEquals(listOf("cat", "s", "and", "more"), spans.map { text.substring(it) })
	}

	@Test
	fun `counted word spans without an image are the word spans`() {
		val text = "one \\![not](x) two"
		assertEquals(4, countedWordSpans(text).count())
		assertEquals(wordSpans(text).toList(), countedWordSpans(text).toList())
	}

	@Test
	fun `counted word spans start right after back-to-back images`() {
		assertEquals(listOf(15..15), countedWordSpans("![a](x)![b](y) z").toList())
	}

	@Test
	fun `counted word spans skip an image inside a code span`() {
		assertEquals(listOf(10..10), countedWordSpans("`![a](b)` c").toList())
	}
}
