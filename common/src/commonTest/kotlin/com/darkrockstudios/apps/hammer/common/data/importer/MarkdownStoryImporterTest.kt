package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ChapterHeadingLevel
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownStoryImporterTest {

	private val importer = MarkdownStoryImporter()

	private fun preview(
		content: String,
		level: ChapterHeadingLevel = ChapterHeadingLevel.H1,
		groups: Boolean = false,
		sourceName: String = "story",
	) = importer.preview(
		sourceName = sourceName,
		content = content,
		options = ImportOptions(
			chapterHeadingLevel = level,
			createChapterGroups = groups,
		),
	)

	@Test
	fun `Single H1 chapter produces one scene`() {
		val md = "# Chapter One\nHello world.\n"
		val result = preview(md)
		assertEquals(1, result.items.size)
		val scene = result.items.first() as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertEquals("Hello world.", scene.markdown)
	}

	@Test
	fun `Multiple H1 chapters produce N scenes`() {
		val md = """
			# Chapter One
			First.
			# Chapter Two
			Second.
			# Chapter Three
			Third.
		""".trimIndent()
		val result = preview(md)
		assertEquals(3, result.items.size)
		assertEquals("Chapter One", result.items[0].name)
		assertEquals("Chapter Two", result.items[1].name)
		assertEquals("Chapter Three", result.items[2].name)
	}

	@Test
	fun `createChapterGroups wraps each chapter in a group with one scene`() {
		val md = """
			# Chapter One
			First.
			# Chapter Two
			Second.
		""".trimIndent()
		val result = preview(md, groups = true)
		assertEquals(2, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Chapter One", group.name)
		assertEquals(1, group.scenes.size)
		assertEquals("Chapter One", group.scenes[0].name)
		assertEquals("First.", group.scenes[0].markdown)
		assertEquals(2, result.totalScenes)
	}

	@Test
	fun `H2 mode treats H1 lines as body content`() {
		val md = """
			# Outer Title
			Intro text.
			## Real Chapter
			Chapter body.
		""".trimIndent()
		val result = preview(md, level = ChapterHeadingLevel.H2)
		// The leading H1 is body content, so it goes into the leading "Untitled" scene
		assertEquals(2, result.items.size)
		val leading = result.items[0] as PreviewItem.Scene
		assertEquals("Untitled", leading.name)
		assertTrue(leading.markdown.contains("# Outer Title"))
		val real = result.items[1] as PreviewItem.Scene
		assertEquals("Real Chapter", real.name)
		assertEquals("Chapter body.", real.markdown)
	}

	@Test
	fun `Pre-heading content becomes leading Untitled scene`() {
		val md = """
			This is a preface.
			Another line.
			# Chapter One
			Body.
		""".trimIndent()
		val result = preview(md)
		assertEquals(2, result.items.size)
		val leading = result.items[0] as PreviewItem.Scene
		assertEquals("Untitled", leading.name)
		assertTrue(leading.markdown.contains("This is a preface."))
		val ch = result.items[1] as PreviewItem.Scene
		assertEquals("Chapter One", ch.name)
	}

	@Test
	fun `No matching headings produces single scene named after source`() {
		val md = """
			Just some text.
			Another line.
		""".trimIndent()
		// "@" is not in the allowed file-name set, so it gets replaced with a space.
		val result = preview(md, sourceName = "my@story")
		assertEquals(1, result.items.size)
		val scene = result.items.first() as PreviewItem.Scene
		assertEquals("my story", scene.name)
		assertTrue(scene.markdown.contains("Just some text."))
	}

	@Test
	fun `Heading with characters illegal in file names is sanitized`() {
		val md = """
			# 1@ Title
			body
			# 2# Chapter I
			more body
		""".trimIndent()
		val result = preview(md)
		assertEquals(2, result.items.size)
		assertEquals("1 Title", result.items[0].name)
		assertEquals("2 Chapter I", result.items[1].name)
	}

	@Test
	fun `Heading with only illegal characters falls back to Untitled`() {
		val md = "# @@@\nbody"
		val result = preview(md)
		assertEquals(1, result.items.size)
		assertEquals("Untitled", result.items[0].name)
	}

	@Test
	fun `Empty content produces empty preview`() {
		val result = preview("")
		assertTrue(result.isEmpty)
		assertEquals(0, result.totalScenes)
	}

	@Test
	fun `Whitespace-only content produces empty preview`() {
		val result = preview("   \n\n   \n")
		assertTrue(result.isEmpty)
	}

	@Test
	fun `Heading without space is recognized`() {
		val md = "#Chapter\nbody"
		val result = preview(md)
		assertEquals(1, result.items.size)
		assertEquals("Chapter", result.items[0].name)
	}

	@Test
	fun `H3 is not treated as H1 chapter`() {
		val md = """
			### Sub-section
			body
		""".trimIndent()
		val result = preview(md)
		// No H1 detected, falls back to single scene named after source
		assertEquals(1, result.items.size)
		val scene = result.items.first() as PreviewItem.Scene
		assertEquals("story", scene.name)
		assertTrue(scene.markdown.contains("### Sub-section"))
	}

	@Test
	fun `CRLF line endings are normalized`() {
		val md = "# Chapter One\r\nFirst.\r\n# Chapter Two\r\nSecond.\r\n"
		val result = preview(md)
		assertEquals(2, result.items.size)
		assertEquals("Chapter One", result.items[0].name)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("First.", scene.markdown)
	}

	@Test
	fun `Blank chapter title falls back to Untitled`() {
		val md = "# \nbody"
		val result = preview(md)
		assertEquals(1, result.items.size)
		assertEquals("Untitled", result.items[0].name)
	}

	@Test
	fun `totalScenes counts scenes across mixed groups and scenes`() {
		val md = """
			# A
			a
			# B
			b
		""".trimIndent()
		val flat = preview(md, groups = false)
		assertEquals(2, flat.totalScenes)
		val grouped = preview(md, groups = true)
		assertEquals(2, grouped.totalScenes)
	}
}
