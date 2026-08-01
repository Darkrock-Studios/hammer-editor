package com.darkrockstudios.apps.hammer.common.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MarkdownEscapesTest {

	@Test
	fun `text without a backslash is returned unchanged and unallocated`() {
		val input = "Alice went down the rabbit hole."
		assertSame(input, unescapeMarkdownText(input))
	}

	@Test
	fun `empty input is returned unchanged`() {
		assertEquals("", unescapeMarkdownText(""))
	}

	@Test
	fun `escapes resolve to their literal character`() {
		assertEquals("well-known", unescapeMarkdownText("well\\-known"))
		assertEquals("1. not a list", unescapeMarkdownText("1\\. not a list"))
		assertEquals("[brackets]", unescapeMarkdownText("\\[brackets\\]"))
		assertEquals("the garden!", unescapeMarkdownText("the garden\\!"))
		assertEquals("mock(turtle)", unescapeMarkdownText("mock\\(turtle\\)"))
	}

	@Test
	fun `an escaped backslash collapses to one backslash`() {
		assertEquals("C:\\path", unescapeMarkdownText("C:\\\\path"))
	}

	@Test
	fun `a backslash before a non-punctuation character is kept`() {
		assertEquals("\\a", unescapeMarkdownText("\\a"))
		assertEquals("\\d{4}", unescapeMarkdownText("\\d{4}"))
	}

	@Test
	fun `a trailing backslash is kept`() {
		assertEquals("end\\", unescapeMarkdownText("end\\"))
	}

	@Test
	fun `escaped emphasis markers become literal characters`() {
		assertEquals("_shape_", unescapeMarkdownText("\\_shape\\_"))
		assertEquals("2 * 3", unescapeMarkdownText("2 \\* 3"))
	}

	@Test
	fun `unescaped markup is left exactly as stored`() {
		assertEquals("**Chapter** One", unescapeMarkdownText("**Chapter** One"))
		assertEquals("user_name", unescapeMarkdownText("user_name"))
		assertEquals("* * *", unescapeMarkdownText("* * *"))
		assertEquals("***", unescapeMarkdownText("***"))
		assertEquals("# Chapter One", unescapeMarkdownText("# Chapter One"))
		assertEquals("run `build` now", unescapeMarkdownText("run `build` now"))
		assertEquals("The cost is 5*4", unescapeMarkdownText("The cost is 5*4"))
	}

	@Test
	fun `line structure is preserved`() {
		assertEquals(
			"a-b\n\nc-d",
			unescapeMarkdownText("a\\-b\n\nc\\-d"),
		)
	}
}
