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
	}

	@Test
	fun `text that only looks like an image still counts`() {
		assertEquals(2, countWords("\\![not](an image)"))
		assertEquals(3, countWords("![open alt](no close"))
		assertEquals(2, countWords("![split\nalt](hammer://image/3)"))
	}
}
