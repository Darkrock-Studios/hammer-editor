package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.RtfSplitStrategy
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RtfStoryImporterTest {

	private val importer = RtfStoryImporter()

	private fun rtf(body: String): ByteArray = "{\\rtf1\\ansi\n$body}".encodeToByteArray()

	private fun preview(
		body: String,
		strategy: RtfSplitStrategy = RtfSplitStrategy.Formatting,
		pattern: String = "(?i)^(chapter|part)\\b",
		groups: Boolean = false,
		sourceName: String = "story",
	) = importer.preview(
		sourceName = sourceName,
		content = rtf(body),
		options = ImportOptions(
			rtfSplitStrategy = strategy,
			rtfChapterPattern = pattern,
			createChapterGroups = groups,
		),
	)

	/** Imports [body] as one unsplit scene, so assertions see the paragraph Markdown verbatim. */
	private fun singleScene(body: String): PreviewItem.Scene =
		preview(body, strategy = RtfSplitStrategy.SingleScene).items[0] as PreviewItem.Scene

	private fun renderHtml(markdown: String): String {
		val flavour = GFMFlavourDescriptor()
		val parsed = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
		return HtmlGenerator(markdown, parsed, flavour).generateHtml()
	}

	@Test
	fun `Formatting splits chapters by outline level into groups and scenes`() {
		val body = """
			\pard\outlinelevel0\fs24 Part One\par
			\pard\outlinelevel1\fs24 Chapter A\par
			\pard\fs24 Body of chapter A.\par
			\pard\outlinelevel1\fs24 Chapter B\par
			\pard\fs24 Body of chapter B.\par
		""".trimIndent()

		val result = preview(body)

		assertEquals(1, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Part One", group.name)
		assertEquals(listOf("Chapter A", "Chapter B"), group.scenes.map { it.name })
		assertEquals("Body of chapter A.", group.scenes[0].markdown)
		assertEquals("Body of chapter B.", group.scenes[1].markdown)
	}

	@Test
	fun `Formatting does not treat outline level 9 body text as a heading`() {
		val body = """
			\pard\outlinelevel0\fs24 Chapter One\par
			\pard\outlinelevel9\fs24 The first body paragraph of the chapter.\par
			\pard\outlinelevel9\fs24 The second body paragraph of the chapter.\par
		""".trimIndent()

		val result = preview(body)

		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertTrue(scene.markdown.contains("The first body paragraph of the chapter."))
		assertTrue(scene.markdown.contains("The second body paragraph of the chapter."))
	}

	@Test
	fun `Formatting treats a larger font as a chapter heading`() {
		val body = """
			\fs48 Chapter One\par
			\fs24 The first body paragraph.\par
			\fs48 Chapter Two\par
			\fs24 The second body paragraph.\par
		""".trimIndent()

		val result = preview(body)

		assertEquals(2, result.items.size)
		assertTrue(result.items.all { it is PreviewItem.Scene })
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("The first body paragraph.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Formatting treats a bold standalone line as a chapter heading`() {
		val body = """
			\fs24\b Chapter One\b0\par
			\fs24 Body text here.\par
		""".trimIndent()

		val result = preview(body)

		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertEquals("Body text here.", scene.markdown)
	}

	@Test
	fun `Formatting preserves inline bold within body as markdown`() {
		val body = """
			\fs48 Chapter One\par
			\fs24 A \b strong\b0  word.\par
		""".trimIndent()

		val result = preview(body)

		val scene = result.items[0] as PreviewItem.Scene
		assertTrue(scene.markdown.contains("**strong**"), "Expected bold markdown, was: ${scene.markdown}")
	}

	@Test
	fun `Bold run whose whitespace is inside the run keeps the delimiters tight`() {
		val scene = singleScene("""\fs24 A\b  strong \b0 word.\par""")

		assertEquals("A **strong** word.", scene.markdown)
	}

	@Test
	fun `Bold run with trailing space before the paragraph end keeps the delimiters tight`() {
		val scene = singleScene("""\fs24 She said \b run \b0\par""")

		assertEquals("She said **run**", scene.markdown)
	}

	@Test
	fun `Emphasis run of only whitespace emits no delimiters`() {
		val scene = singleScene("""\fs24 word\b  \b0 next.\par""")

		assertEquals("word next.", scene.markdown)
	}

	@Test
	fun `Bold spanning a tab keeps the delimiters against the text`() {
		val scene = singleScene("""\fs24 A\b  strong\tab\tab\b0 word.\par""")

		assertEquals("A **strong**\t\tword.", scene.markdown)
	}

	@Test
	fun `Bold ended by a group close keeps the delimiters tight`() {
		val scene = singleScene("""\fs24 A {\b strong }word.\par""")

		assertEquals("A **strong** word.", scene.markdown)
	}

	@Test
	fun `Emphasis is closed at a hard line break`() {
		val scene = singleScene("""\fs24 \b first\line second \b0 last.\par""")

		assertEquals("**first**  \n**second** last.", scene.markdown)
	}

	@Test
	fun `Overlapping bold and italic runs stay balanced`() {
		val scene = singleScene("""\fs24 \i one \b two \i0 three \b0 four.\par""")

		assertEquals("*one **two*** **three** four.", scene.markdown)
	}

	@Test
	fun `Italic uses asterisks so intra-word emphasis renders`() {
		val scene = singleScene("""\fs24 foo\i bar\i0 baz.\par""")

		assertEquals("foo*bar*baz.", scene.markdown)
	}

	@Test
	fun `Nested bold and italic close inside the trailing whitespace`() {
		val scene = singleScene("""\fs24 A \b\i very loud \i0\b0 word.\par""")

		assertEquals("A ***very loud*** word.", scene.markdown)
	}

	@Test
	fun `Emphasis that ends with the paragraph is still closed`() {
		val scene = singleScene("""\fs24 He was \i gone\par""")

		assertEquals("He was *gone*", scene.markdown)
	}

	@Test
	fun `A paragraph starting with a dash is escaped instead of importing as a list`() {
		val scene = singleScene("""\fs24 - a spoken line.\par""")

		assertEquals("\\- a spoken line.", scene.markdown)
	}

	@Test
	fun `A paragraph starting with a number is escaped instead of importing as a list`() {
		val scene = singleScene("""\fs24 1. Never speak of it.\par""")

		assertEquals("1\\. Never speak of it.", scene.markdown)
	}

	@Test
	fun `Markdown special characters in prose are escaped`() {
		val scene = singleScene("""\fs24 Wait! (really) 1+1 a|b\par""")

		assertEquals("Wait\\! \\(really\\) 1\\+1 a\\|b", scene.markdown)
	}

	@Test
	fun `Emphasis with whitespace inside the run renders as emphasis`() {
		val scene = singleScene("""\fs24 She said \b run \b0 and\i then\i0 fled.\par""")

		val html = renderHtml(scene.markdown)
		assertTrue(html.contains("<strong>run</strong>"), "Expected bold to render, was: $html")
		assertTrue(html.contains("<em>then</em>"), "Expected italics to render, was: $html")
	}

	@Test
	fun `createChapterGroups wraps single-tier scenes in groups`() {
		val body = """
			\fs48 Chapter One\par
			\fs24 The first chapter has a reasonably long body.\par
			\fs48 Chapter Two\par
			\fs24 The second chapter also has a fairly long body.\par
		""".trimIndent()

		val result = preview(body, groups = true)

		assertEquals(2, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Chapter One", group.name)
		assertEquals(1, group.scenes.size)
	}

	@Test
	fun `Pattern strategy splits on matching paragraphs only`() {
		val body = """
			\fs24 Chapter One\par
			\fs24 Body A.\par
			\fs24 Chapter Two\par
			\fs24 Body B.\par
		""".trimIndent()

		val result = preview(body, strategy = RtfSplitStrategy.Pattern)

		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("Body A.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Pattern strategy with no formatting cues keeps prose flat without the pattern`() {
		val body = """
			\fs24 The wind rose.\par
			\fs24 Then it fell.\par
		""".trimIndent()

		val result = preview(body, strategy = RtfSplitStrategy.Pattern)

		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("story", scene.name)
	}

	@Test
	fun `Invalid pattern falls back to a single scene`() {
		val body = """
			\fs24 Chapter One\par
			\fs24 Body.\par
		""".trimIndent()

		val result = preview(body, strategy = RtfSplitStrategy.Pattern, pattern = "[")

		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `SingleScene strategy imports everything as one scene`() {
		val body = """
			\fs48 Chapter One\par
			\fs24 Body of one.\par
			\fs48 Chapter Two\par
			\fs24 Body of two.\par
		""".trimIndent()

		val result = preview(body, strategy = RtfSplitStrategy.SingleScene)

		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("story", scene.name)
		assertTrue(scene.markdown.contains("Chapter One"))
		assertTrue(scene.markdown.contains("Body of two."))
	}

	@Test
	fun `Non-body destinations are ignored`() {
		val body = """
			{\fonttbl{\f0 Times New Roman;}}{\info{\title Secret Title}}
			\fs24 Hello world.\par
		""".trimIndent()

		val result = preview(body, strategy = RtfSplitStrategy.SingleScene)

		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Hello world.", scene.markdown)
		assertFalse(scene.markdown.contains("Times"))
		assertFalse(scene.markdown.contains("Secret"))
	}

	@Test
	fun `Empty document produces an empty preview`() {
		val result = preview("", strategy = RtfSplitStrategy.SingleScene)
		assertTrue(result.isEmpty)
	}
}
