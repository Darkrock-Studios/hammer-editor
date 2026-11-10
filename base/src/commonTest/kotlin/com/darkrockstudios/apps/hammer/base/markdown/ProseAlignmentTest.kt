package com.darkrockstudios.apps.hammer.base.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProseAlignmentTest {

	private fun AlignedProse.alignments(): List<ProseAlign?> =
		markdown.split('\n').indices.map { alignmentOfLine(it) }

	@Test
	fun `the editor's wrapper comes out and its line takes the alignment`() {
		val aligned = stripAlignmentWrappers("before\n<div align=\"center\">\n\ncentred\n\n</div>\n\nafter")

		assertEquals("before\ncentred\nafter", aligned.markdown)
		assertEquals(listOf(null, ProseAlign.CENTER, null), aligned.alignments())
	}

	@Test
	fun `a run keeps its blank lines and every line of it is aligned`() {
		val aligned = stripAlignmentWrappers("<div align=\"right\">\n\none\n\ntwo\n\n</div>\n\n<div align=\"justify\">\n\nthree\n\n</div>")

		assertEquals("one\n\ntwo\nthree", aligned.markdown)
		assertEquals(listOf(ProseAlign.RIGHT, ProseAlign.RIGHT, ProseAlign.RIGHT, ProseAlign.JUSTIFY), aligned.alignments())
	}

	@Test
	fun `the document's last line break is kept`() {
		assertEquals("x\ny\n", stripAlignmentWrappers("x\n<div align=\"right\">\n\ny\n\n</div>\n").markdown)
	}

	@Test
	fun `a foreign wrapper reads as a p, by its style, or without blank lines`() {
		val aligned = stripAlignmentWrappers("<p align='right'>\nright\n</p>\n<DIV style=\"color: red; text-align: center\">\n\ncentred\n\n</DIV>")

		assertEquals("right\ncentred", aligned.markdown)
		assertEquals(listOf(ProseAlign.RIGHT, ProseAlign.CENTER), aligned.alignments())
	}

	@Test
	fun `a wrapper on one line reads`() {
		val aligned = stripAlignmentWrappers("before\n<div align=\"center\">centred *text*</div>\nafter")

		assertEquals("before\ncentred *text*\nafter", aligned.markdown)
		assertEquals(listOf(null, ProseAlign.CENTER, null), aligned.alignments())
	}

	@Test
	fun `text may share a line with either tag`() {
		val aligned = stripAlignmentWrappers("<div align=\"right\">first\nmiddle\nlast</div>\n<p align=\"center\">\ncentred\n</p> after")

		assertEquals("first\nmiddle\nlast\ncentred\nafter", aligned.markdown)
		assertEquals(
			listOf(ProseAlign.RIGHT, ProseAlign.RIGHT, ProseAlign.RIGHT, ProseAlign.CENTER, null),
			aligned.alignments(),
		)
	}

	@Test
	fun `a closing tag of another name does not close a wrapper`() {
		val markdown = "<div align=\"center\">\ntext\n</p>"
		val aligned = stripAlignmentWrappers(markdown)

		assertEquals(markdown, aligned.markdown)
		assertFalse(aligned.hasAlignments)
	}

	@Test
	fun `a closing tag escaped or in a code span does not close a wrapper`() {
		val aligned = stripAlignmentWrappers("<div align=\"center\">\none \\</div>\n`two </div>`\nthree\n</div>")

		assertEquals("one \\</div>\n`two </div>`\nthree", aligned.markdown)
		assertEquals(listOf(ProseAlign.CENTER, ProseAlign.CENTER, ProseAlign.CENTER), aligned.alignments())
	}

	@Test
	fun `an escaped opening tag is text`() {
		val markdown = "\\<div align=\"center\">text\\</div>"
		assertEquals(markdown, stripAlignmentWrappers(markdown).markdown)
	}

	@Test
	fun `an opening tag with no closing one is text`() {
		val markdown = "<div align=\"center\">\n\ntext"
		val aligned = stripAlignmentWrappers(markdown)

		assertEquals(markdown, aligned.markdown)
		assertFalse(aligned.hasAlignments)
	}

	@Test
	fun `a wrapper inside a code fence is code`() {
		val markdown = "```\n<div align=\"center\">\n\ncode\n\n</div>\n```"

		assertEquals(markdown, stripAlignmentWrappers(markdown).markdown)
	}

	@Test
	fun `an offset finds its line's alignment`() {
		val aligned = stripAlignmentWrappers("a\n<div align=\"center\">\n\nbc\n\n</div>\n\nd")

		assertEquals("a\nbc\nd", aligned.markdown)
		assertEquals(null, aligned.alignmentAt(0))
		assertEquals(ProseAlign.CENTER, aligned.alignmentAt(2))
		assertEquals(ProseAlign.CENTER, aligned.alignmentAt(3))
		assertEquals(null, aligned.alignmentAt(5))
	}
}
