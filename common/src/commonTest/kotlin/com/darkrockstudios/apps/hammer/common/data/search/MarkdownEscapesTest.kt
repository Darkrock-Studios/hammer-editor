package com.darkrockstudios.apps.hammer.common.data.search

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownEscapesTest {

	@Test
	fun `text without a backslash is returned unchanged`() {
		val input = "Plain prose, no escapes."
		assertEquals(input, unescapeMarkdown(input))
	}

	@Test
	fun `empty input is returned unchanged`() {
		assertEquals("", unescapeMarkdown(""))
	}

	@Test
	fun `backslash before ASCII punctuation resolves to the bare character`() {
		assertEquals("*", unescapeMarkdown("\\*"))
		assertEquals("_", unescapeMarkdown("\\_"))
		assertEquals("#", unescapeMarkdown("\\#"))
		assertEquals("\\", unescapeMarkdown("\\\\"))
		assertEquals("[link]", unescapeMarkdown("\\[link\\]"))
	}

	@Test
	fun `escapes resolve inside a sentence`() {
		assertEquals("use *stars* and _unders_", unescapeMarkdown("use \\*stars\\* and \\_unders\\_"))
		assertEquals("well-known", unescapeMarkdown("well\\-known"))
		assertEquals("the garden!", unescapeMarkdown("the garden\\!"))
		assertEquals("mock(turtle)", unescapeMarkdown("mock\\(turtle\\)"))
		assertEquals("1. not a list", unescapeMarkdown("1\\. not a list"))
	}

	@Test
	fun `a backslash before a non-punctuation character is preserved`() {
		// Only ASCII punctuation is a valid CommonMark escape; everything else keeps the backslash.
		assertEquals("\\a", unescapeMarkdown("\\a"))
		assertEquals("C:\\temp", unescapeMarkdown("C:\\temp"))
		assertEquals("\\d{4}", unescapeMarkdown("\\d{4}"))
	}

	@Test
	fun `a trailing backslash is preserved`() {
		assertEquals("end\\", unescapeMarkdown("end\\"))
	}

	@Test
	fun `unescaped markup is left exactly as stored`() {
		assertEquals("**Chapter** One", unescapeMarkdown("**Chapter** One"))
		assertEquals("user_name", unescapeMarkdown("user_name"))
		assertEquals("* * *", unescapeMarkdown("* * *"))
		assertEquals("***", unescapeMarkdown("***"))
		assertEquals("# Chapter One", unescapeMarkdown("# Chapter One"))
		assertEquals("run `build` now", unescapeMarkdown("run `build` now"))
		assertEquals("The cost is 5*4", unescapeMarkdown("The cost is 5*4"))
	}

	@Test
	fun `line structure is preserved`() {
		assertEquals("a-b\n\nc-d", unescapeMarkdown("a\\-b\n\nc\\-d"))
	}
}
