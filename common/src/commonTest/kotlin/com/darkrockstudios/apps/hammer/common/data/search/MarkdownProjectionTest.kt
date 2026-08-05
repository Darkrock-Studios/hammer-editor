package com.darkrockstudios.apps.hammer.common.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MarkdownProjectionTest {

	@Test
	fun `plain prose is returned unchanged and unallocated`() {
		val input = "Alice went down the rabbit hole."
		assertSame(input, projectMarkdownToPlainText(input))
	}

	@Test
	fun `empty input is returned unchanged`() {
		assertEquals("", projectMarkdownToPlainText(""))
	}

	@Test
	fun `backslash escapes resolve to their literal character`() {
		assertEquals("well-known", projectMarkdownToPlainText("well\\-known"))
		assertEquals("1. not a list", projectMarkdownToPlainText("1\\. not a list"))
		assertEquals("[brackets]", projectMarkdownToPlainText("\\[brackets\\]"))
	}

	@Test
	fun `escaped backslash collapses to one backslash`() {
		assertEquals("C:\\path", projectMarkdownToPlainText("C:\\\\path"))
	}

	@Test
	fun `a backslash before a non-punctuation character is kept`() {
		assertEquals("\\a", projectMarkdownToPlainText("\\a"))
	}

	@Test
	fun `a trailing backslash is kept`() {
		assertEquals("end\\", projectMarkdownToPlainText("end\\"))
	}

	@Test
	fun `paired emphasis markers are dropped so phrases join up`() {
		assertEquals("Chapter One", projectMarkdownToPlainText("**Chapter** One"))
		assertEquals("quick brown", projectMarkdownToPlainText("_quick_ brown"))
		assertEquals("run build now", projectMarkdownToPlainText("run `build` now"))
	}

	@Test
	fun `nested emphasis is unwrapped`() {
		assertEquals("very loud indeed", projectMarkdownToPlainText("**very _loud_ indeed**"))
		assertEquals("shout", projectMarkdownToPlainText("***shout***"))
	}

	@Test
	fun `escaped markers survive as literal characters`() {
		assertEquals("_shape_", projectMarkdownToPlainText("\\_shape\\_"))
		assertEquals("2 * 3", projectMarkdownToPlainText("2 \\* 3"))
	}

	@Test
	fun `escaping is resolved before markers are dropped`() {
		assertEquals("a_b and c", projectMarkdownToPlainText("a\\_b and _c_"))
	}

	@Test
	fun `underscores inside a word are literal`() {
		assertEquals("user_name", projectMarkdownToPlainText("user_name"))
		assertEquals("my_table_name", projectMarkdownToPlainText("my_table_name"))
		assertEquals("snake_case and emphasis", projectMarkdownToPlainText("snake_case and _emphasis_"))
	}

	@Test
	fun `markers that cannot pair off are literal`() {
		assertEquals("2 * 3 = 6", projectMarkdownToPlainText("2 * 3 = 6"))
		assertEquals("***", projectMarkdownToPlainText("***"))
		assertEquals("an *unclosed aside", projectMarkdownToPlainText("an *unclosed aside"))
		assertEquals("a ` stray tick", projectMarkdownToPlainText("a ` stray tick"))
	}

	@Test
	fun `emphasis markers inside a code span are literal`() {
		assertEquals("call my_func now", projectMarkdownToPlainText("call `my_func` now"))
	}

	@Test
	fun `leading block markers are stripped from each line`() {
		assertEquals("Chapter One", projectMarkdownToPlainText("# Chapter One"))
		assertEquals("a quote", projectMarkdownToPlainText("> a quote"))
		assertEquals("a bullet", projectMarkdownToPlainText("- a bullet"))
		assertEquals("Chapter One\n\nThe body", projectMarkdownToPlainText("# Chapter One\n\nThe body"))
	}

	@Test
	fun `ordered list markers are left alone so years survive`() {
		assertEquals("1984. The year everything changed", projectMarkdownToPlainText("1984. The year everything changed"))
		assertEquals("3) Meet at the docks", projectMarkdownToPlainText("3) Meet at the docks"))
	}

	@Test
	fun `a marker that is not followed by a space is prose`() {
		assertEquals("-15 degrees", projectMarkdownToPlainText("-15 degrees"))
		assertEquals("#hashtag here", projectMarkdownToPlainText("#hashtag here"))
	}

	@Test
	fun `code delimiters do not pair across a paragraph break`() {
		assertEquals(
			"It`s here.\n\nThe Chapter One part.\n\nDon`t stop.",
			projectMarkdownToPlainText("It`s here.\n\nThe **Chapter** One part.\n\nDon`t stop."),
		)
	}

	@Test
	fun `emphasis does not pair across a paragraph break`() {
		assertEquals(
			"See note *below\n\nThe rate is 5* higher",
			projectMarkdownToPlainText("See note *below\n\nThe rate is 5* higher"),
		)
	}

	@Test
	fun `emphasis still pairs across a soft line break`() {
		assertEquals("The long\ntitle here", projectMarkdownToPlainText("The **long\ntitle** here"))
	}

	@Test
	fun `a document with escapes but no emphasis still resolves them`() {
		assertEquals(
			"The well-known garden! (and its gate)",
			projectMarkdownToPlainText("The well\\-known garden\\! \\(and its gate\\)"),
		)
	}

	@Test
	fun `title line strips block markers and inline markup`() {
		assertEquals("Chapter One", markdownTitleLine("# **Chapter** One\n\nbody"))
		assertEquals("A quoted thought", markdownTitleLine("> A quoted thought"))
		assertEquals("First item", markdownTitleLine("- First item\n- Second"))
	}

	@Test
	fun `title line keeps a leading number`() {
		assertEquals("1984. The year everything changed", markdownTitleLine("1984. The year everything changed"))
	}

	@Test
	fun `title line pairs emphasis that closes on the next line`() {
		assertEquals("The long", markdownTitleLine("The **long\ntitle** here"))
	}

	@Test
	fun `title line skips leading blank lines`() {
		assertEquals("Actual title", markdownTitleLine("\n   \nActual title\nmore"))
		assertEquals("", markdownTitleLine("   \n\n"))
	}

	@Test
	fun `a title line of pure markup falls back to the raw line`() {
		assertEquals("***", markdownTitleLine("***\nbody"))
		assertEquals("#", markdownTitleLine("#\nbody"))
	}

	@Test
	fun `an emphasized title keeps its words`() {
		assertEquals("Once upon a time", markdownTitleLine("*Once upon a time*"))
	}
}
