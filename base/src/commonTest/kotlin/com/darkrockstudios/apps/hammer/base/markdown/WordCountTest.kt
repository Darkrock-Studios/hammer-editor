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
}
