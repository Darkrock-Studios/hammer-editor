package com.darkrockstudios.apps.hammer.common.components.projecthome

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryExportCommonTest {

	@Test
	fun `text without a backslash is returned unchanged`() {
		val input = "Plain prose, no escapes."
		assertEquals(input, unescapeMarkdown(input))
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
	fun `emphasis markers are unescaped inside a sentence`() {
		assertEquals("use *stars* and _unders_", unescapeMarkdown("use \\*stars\\* and \\_unders\\_"))
	}

	@Test
	fun `a backslash before a non-punctuation character is preserved`() {
		// Only ASCII punctuation is a valid CommonMark escape; everything else keeps the backslash.
		assertEquals("\\a", unescapeMarkdown("\\a"))
		assertEquals("C:\\temp", unescapeMarkdown("C:\\temp"))
	}

	@Test
	fun `a trailing backslash is preserved`() {
		assertEquals("end\\", unescapeMarkdown("end\\"))
	}
}
