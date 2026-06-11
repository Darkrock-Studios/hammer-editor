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
	fun `paragraphs are split on blank lines and soft wraps become spaces`() {
		val blocks = parseProseMarkdown("First line\ncontinues here.\n\nSecond paragraph.")

		assertEquals(2, blocks.size)
		assertEquals("First line continues here.", paragraph(blocks[0]).spans.plain())
		assertEquals("Second paragraph.", paragraph(blocks[1]).spans.plain())
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
		assertEquals("plain item", list.items[0].plain())
		assertEquals("bold item", list.items[1].plain())
		assertTrue(list.items[1].first { it.text == "bold" }.bold)
	}

	@Test
	fun `ordered list is marked ordered`() {
		val blocks = parseProseMarkdown("1. first\n2. second")

		val list = assertIs<ProseBlock.Listing>(blocks.single())
		assertTrue(list.ordered)
		assertEquals(listOf("first", "second"), list.items.map { it.plain() })
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
	fun `hard line break becomes a newline within the paragraph`() {
		val blocks = parseProseMarkdown("line one  \nline two")

		assertEquals("line one\nline two", paragraph(blocks.single()).spans.plain())
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
