package components.projecthome

import com.darkrockstudios.apps.hammer.common.components.projecthome.ProseBlock
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProseSpan
import com.darkrockstudios.apps.hammer.common.components.projecthome.argbHexToPdfColor
import com.darkrockstudios.apps.hammer.common.components.projecthome.parseProseMarkdown
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfProseMarkdownTest {

	private fun List<ProseSpan>.plain(): String = joinToString("") { it.text }

	private fun paragraph(block: ProseBlock): ProseBlock.Paragraph = assertIs(block)

	@Test
	fun `blank input produces no blocks`() {
		assertTrue(parseProseMarkdown("").isEmpty())
		assertTrue(parseProseMarkdown("   \n\n  ").isEmpty())
	}

	@Test
	fun `every authored line becomes its own paragraph`() {
		val blocks = parseProseMarkdown("First line.\nSecond line.\nThird line.")

		assertEquals(3, blocks.size)
		assertEquals("First line.", paragraph(blocks[0]).spans.plain())
		assertEquals("Second line.", paragraph(blocks[1]).spans.plain())
		assertEquals("Third line.", paragraph(blocks[2]).spans.plain())
	}

	@Test
	fun `a blank line between passages survives as a blank block`() {
		val blocks = parseProseMarkdown("First passage.\n\nSecond passage.")

		assertEquals(3, blocks.size)
		assertEquals("First passage.", paragraph(blocks[0]).spans.plain())
		assertIs<ProseBlock.Blank>(blocks[1])
		assertEquals("Second passage.", paragraph(blocks[2]).spans.plain())
	}

	@Test
	fun `each blank line of a run counts`() {
		val blocks = parseProseMarkdown("First passage.\n\n\n\nSecond passage.")

		assertEquals(3, blocks.count { it is ProseBlock.Blank })
	}

	@Test
	fun `a blank line beside a heading is left to the heading's own spacing`() {
		val blocks = parseProseMarkdown("## Chapter One\n\nThe prose.\n\n\n## Chapter Two")

		assertEquals(0, blocks.count { it is ProseBlock.Blank })
	}

	@Test
	fun `Windows line endings lay out like any other`() {
		val unix = parseProseMarkdown("First line.\nSecond line.\n\nAfter a blank line.")
		val windows = parseProseMarkdown("First line.\r\nSecond line.\r\n\r\nAfter a blank line.")

		assertEquals(unix, windows)
	}

	@Test
	fun `emphasis produces styled spans`() {
		val blocks = parseProseMarkdown("Plain **bold** and *italic* and ***both***.")

		val spans = paragraph(blocks.single()).spans
		assertEquals("Plain bold and italic and both.", spans.plain())

		val bold = spans.single { it.text == "bold" }
		assertTrue(bold.bold)
		assertTrue(!bold.italic)

		val italic = spans.single { it.text == "italic" }
		assertTrue(italic.italic)
		assertTrue(!italic.bold)

		val both = spans.single { it.text == "both" }
		assertTrue(both.bold)
		assertTrue(both.italic)
	}

	@Test
	fun `strikethrough produces styled spans`() {
		val blocks = parseProseMarkdown("This is ~~gone~~ now.")

		val spans = paragraph(blocks.single()).spans
		assertEquals("This is gone now.", spans.plain())
		assertTrue(spans.single { it.text == "gone" }.strikethrough)
	}

	@Test
	fun `backslash escapes resolve to bare punctuation without styling`() {
		val blocks = parseProseMarkdown("""Keep \*these\* literal \(and these\).""")

		val spans = paragraph(blocks.single()).spans
		assertEquals("Keep *these* literal (and these).", spans.plain())
		assertTrue(spans.none { it.italic || it.bold })
	}

	@Test
	fun `atx headings carry their level`() {
		val blocks = parseProseMarkdown("# Title\n\n### Sub\n\nBody.")

		val h1 = assertIs<ProseBlock.Heading>(blocks[0])
		assertEquals(1, h1.level)
		assertEquals("Title", h1.spans.plain())

		val h3 = assertIs<ProseBlock.Heading>(blocks[1])
		assertEquals(3, h3.level)
		assertEquals("Sub", h3.spans.plain())

		paragraph(blocks[2])
	}

	@Test
	fun `horizontal rule becomes a rule block`() {
		val blocks = parseProseMarkdown("Before.\n\n---\n\nAfter.")

		assertEquals(3, blocks.size)
		assertIs<ProseBlock.Rule>(blocks[1])
	}

	@Test
	fun `unordered list keeps inline styling per item`() {
		val blocks = parseProseMarkdown("- plain item\n- **bold** item")

		val list = assertIs<ProseBlock.Listing>(blocks.single())
		assertTrue(!list.ordered)
		assertEquals(2, list.items.size)
		assertEquals("plain item", list.items[0].spans.plain())
		assertEquals("bold item", list.items[1].spans.plain())
		assertTrue(list.items[1].spans.first { it.text == "bold" }.bold)
	}

	@Test
	fun `ordered list is marked ordered`() {
		val blocks = parseProseMarkdown("1. first\n2. second")

		val list = assertIs<ProseBlock.Listing>(blocks.single())
		assertTrue(list.ordered)
		assertEquals(listOf("first", "second"), list.items.map { it.spans.plain() })
	}

	@Test
	fun `nested list items carry their nesting level in reading order`() {
		val blocks = parseProseMarkdown("- parent\n    - child\n- sibling")

		val list = assertIs<ProseBlock.Listing>(blocks.single())
		assertEquals(listOf("parent", "child", "sibling"), list.items.map { it.spans.plain() })
		assertEquals(listOf(0, 1, 0), list.items.map { it.level })
	}

	@Test
	fun `inline link is styled with its destination`() {
		val blocks = parseProseMarkdown("See [the site](https://example.com) today.")

		val spans = paragraph(blocks.single()).spans
		assertEquals("See the site today.", spans.plain())
		assertEquals("https://example.com", spans.single { it.text == "the site" }.link)
		assertNull(spans.first().link)
	}

	@Test
	fun `standalone link paragraph is a single linked span`() {
		val blocks = parseProseMarkdown("[the site](https://example.com)")

		val spans = paragraph(blocks.single()).spans
		val span = spans.single()
		assertEquals("the site", span.text)
		assertEquals("https://example.com", span.link)
	}

	@Test
	fun `inline code is flagged and keeps its text verbatim`() {
		val blocks = parseProseMarkdown("Run `the *command*` now.")

		val spans = paragraph(blocks.single()).spans
		val code = spans.single { it.code }
		assertEquals("the *command*", code.text)
	}

	@Test
	fun `hard line break parts the lines just as a plain newline does`() {
		val blocks = parseProseMarkdown("line one  \nline two")

		assertEquals(2, blocks.size)
		assertEquals("line one", paragraph(blocks[0]).spans.plain())
		assertEquals("line two", paragraph(blocks[1]).spans.plain())
	}

	@Test
	fun `a quoted passage keeps its lines`() {
		val blocks = parseProseMarkdown("> quote line one\n> quote line two")

		val quote = assertIs<ProseBlock.Quote>(blocks.single())
		assertEquals(2, quote.paragraphs.size)
		assertEquals("quote line one", quote.paragraphs[0].plain())
		assertEquals("quote line two", quote.paragraphs[1].plain())
	}

	@Test
	fun `fenced code block keeps lines including blank ones`() {
		val blocks = parseProseMarkdown("```\nfirst\n\nsecond\n```")

		val code = assertIs<ProseBlock.CodeBlock>(blocks.single())
		assertEquals("first\n\nsecond", code.code)
	}

	@Test
	fun `blockquote collects its paragraphs`() {
		val blocks = parseProseMarkdown("> quoted text\n>\n> more quoted")

		val quote = assertIs<ProseBlock.Quote>(blocks.single())
		assertEquals(listOf("quoted text", "more quoted"), quote.paragraphs.map { it.plain() })
	}

	@Test
	fun `pipe table parses header and rows`() {
		val blocks = parseProseMarkdown("| a | b |\n|---|---|\n| one | **two** |")

		val table = assertIs<ProseBlock.Table>(blocks.single())
		assertEquals(listOf("a", "b"), table.header.map { it.plain() })
		assertEquals(listOf("one", "two"), table.rows.single().map { it.plain() })
		assertTrue(table.rows.single()[1].single().bold)
	}

	@Test
	fun `argb hex theme colors convert to pdf colors`() {
		val color = argbHexToPdfColor("#FF3366CC")
		assertEquals(0x33 / 255f, color!!.red)
		assertEquals(0x66 / 255f, color.green)
		assertEquals(0xCC / 255f, color.blue)

		assertEquals(color, argbHexToPdfColor("3366CC"))
		assertNull(argbHexToPdfColor("not-a-color"))
		assertNull(argbHexToPdfColor("#12345"))
	}
}
