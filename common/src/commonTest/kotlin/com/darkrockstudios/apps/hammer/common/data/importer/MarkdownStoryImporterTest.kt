package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.DEFAULT_CHAPTER_PATTERN
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownStoryImporterTest {

	private val importer = MarkdownStoryImporter()

	private fun preview(
		content: String,
		strategy: MarkdownSplitStrategy = MarkdownSplitStrategy.H1,
		groups: Boolean = false,
		sourceName: String = "story",
		pattern: String = DEFAULT_CHAPTER_PATTERN,
	) = importer.preview(
		sourceName = sourceName,
		content = content.encodeToByteArray(),
		options = ImportOptions(
			markdownSplitStrategy = strategy,
			createChapterGroups = groups,
			markdownChapterPattern = pattern,
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
	fun `H2 mode treats a shallower H1 as a group wrapping its scenes`() {
		val md = """
			# Outer Title
			## Real Chapter
			Chapter body.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(1, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Outer Title", group.name)
		assertEquals(1, group.scenes.size)
		assertEquals("Real Chapter", group.scenes[0].name)
		assertEquals("Chapter body.", group.scenes[0].markdown)
	}

	@Test
	fun `H2 mode keeps group-intro prose as a leading Untitled scene inside the group`() {
		val md = """
			# Outer Title
			Intro text.
			## Real Chapter
			Chapter body.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(1, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Outer Title", group.name)
		assertEquals(2, group.scenes.size)
		assertEquals("Untitled", group.scenes[0].name)
		assertEquals("Intro text.", group.scenes[0].markdown)
		assertEquals("Real Chapter", group.scenes[1].name)
		assertEquals("Chapter body.", group.scenes[1].markdown)
	}

	@Test
	fun `Headings deeper than the chosen level stay as scene body`() {
		val md = """
			## Chapter One
			Intro.
			### A subsection
			More text.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertTrue(scene.markdown.contains("### A subsection"))
		assertTrue(scene.markdown.contains("More text."))
	}

	@Test
	fun `Multiple H1 groups each keep their H2 scenes`() {
		val md = """
			# Part One
			## Chapter A
			a body
			## Chapter B
			b body
			# Part Two
			## Chapter C
			c body
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(2, result.items.size)
		val partOne = result.items[0] as PreviewItem.Group
		assertEquals("Part One", partOne.name)
		assertEquals(listOf("Chapter A", "Chapter B"), partOne.scenes.map { it.name })
		val partTwo = result.items[1] as PreviewItem.Group
		assertEquals("Part Two", partTwo.name)
		assertEquals(listOf("Chapter C"), partTwo.scenes.map { it.name })
	}

	@Test
	fun `H2 scenes before the first group heading stay top-level`() {
		val md = """
			## Chapter A
			a body
			# Part Two
			## Chapter B
			b body
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(2, result.items.size)
		val sceneA = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter A", sceneA.name)
		val partTwo = result.items[1] as PreviewItem.Group
		assertEquals("Part Two", partTwo.name)
		assertEquals(listOf("Chapter B"), partTwo.scenes.map { it.name })
	}

	@Test
	fun `H1 selection with no shallower headings produces flat scenes`() {
		val md = """
			# Chapter One
			a
			# Chapter Two
			b
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H1)
		assertEquals(2, result.items.size)
		assertTrue(result.items.all { it is PreviewItem.Scene })
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Leading BOM does not hide the first heading`() {
		val md = "﻿# Chapter One\nbody"
		val result = preview(md)
		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertEquals("body", scene.markdown)
	}

	@Test
	fun `Indented heading up to three spaces is still recognized`() {
		val md = "   # Chapter One\nbody"
		val result = preview(md)
		assertEquals(1, result.items.size)
		assertEquals("Chapter One", result.items[0].name)
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
	fun `Hammer export round-trips H1 title to group and H2 chapters to scenes`() {
		val md = """
			# My Novel

			## 1. Chapter One

			Chapter one body.

			## 2. Chapter Two

			Chapter two body.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(1, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("My Novel", group.name)
		assertEquals(2, group.scenes.size)
		assertEquals("1. Chapter One", group.scenes[0].name)
		assertEquals("Chapter one body.", group.scenes[0].markdown)
		assertEquals("2. Chapter Two", group.scenes[1].name)
		assertEquals("Chapter two body.", group.scenes[1].markdown)
	}

	@Test
	fun `Auto detects a leading H1 title and splits the H2 chapters as flat scenes`() {
		val md = """
			# My Novel

			## Chapter One
			First.

			## Chapter Two
			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals("My Novel", result.title)
		assertEquals(2, result.items.size)
		assertTrue(result.items.all { it is PreviewItem.Scene })
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Auto keeps flat H1 chapters as scenes with no title`() {
		val md = """
			# Chapter One
			a
			# Chapter Two
			b
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(null, result.title)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Auto folds parts into groups beneath a detected title`() {
		val md = """
			# My Novel
			## Part One
			### Chapter A
			a
			### Chapter B
			b
			## Part Two
			### Chapter C
			c
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals("My Novel", result.title)
		assertEquals(2, result.items.size)
		val partOne = result.items[0] as PreviewItem.Group
		assertEquals("Part One", partOne.name)
		assertEquals(listOf("Chapter A", "Chapter B"), partOne.scenes.map { it.name })
		val partTwo = result.items[1] as PreviewItem.Group
		assertEquals(listOf("Chapter C"), partTwo.scenes.map { it.name })
	}

	@Test
	fun `Auto with a title and no chapters yields a single scene and the title`() {
		val md = """
			# My Novel
			Just prose, no chapters.
			More prose.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals("My Novel", result.title)
		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertTrue(scene.markdown.contains("Just prose, no chapters."))
	}

	@Test
	fun `Setext equals underline is an H1 chapter`() {
		val md = """
			Chapter One
			===========
			First.

			Chapter Two
			===
			Second.
		""".trimIndent()
		val result = preview(md)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
		assertEquals("Second.", (result.items[1] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Setext dash underline is an H2 chapter`() {
		val md = """
			Chapter One
			-----------
			First.

			Chapter Two
			-----------
			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.H2)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Setext underline is dropped from the scene body`() {
		val md = "Chapter One\n===\nFirst."
		val result = preview(md)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("First.", scene.markdown)
	}

	@Test
	fun `Dash rule with no paragraph above it is a thematic break not a heading`() {
		val md = """
			Scene one ends.

			---

			Scene two begins.
		""".trimIndent()
		val result = preview(md, sourceName = "story")
		assertEquals(1, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("story", scene.name)
		assertTrue(scene.markdown.contains("---"))
	}

	@Test
	fun `Front matter fence is not a heading`() {
		val md = """
			---
			title: My Novel
			---

			# Chapter One
			First.
		""".trimIndent()
		val result = preview(md)
		assertEquals(2, result.items.size)
		assertEquals("Untitled", result.items[0].name)
		assertTrue((result.items[0] as PreviewItem.Scene).markdown.contains("title: My Novel"))
		assertEquals("Chapter One", result.items[1].name)
	}

	@Test
	fun `Auto treats a Setext equals title above Setext dash chapters as the story title`() {
		val md = """
			My Novel
			========

			Chapter One
			-----------
			First.

			Chapter Two
			-----------
			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals("My Novel", result.title)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Mixed Setext and ATX headings share the same level space`() {
		val md = """
			# Chapter One
			First.
			Chapter Two
			===========
			Second.
		""".trimIndent()
		val result = preview(md)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Setext CRLF line endings are normalized`() {
		val md = "Chapter One\r\n===\r\nFirst.\r\n\r\nChapter Two\r\n===\r\nSecond.\r\n"
		val result = preview(md)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `A Setext title indented four spaces is not a heading`() {
		val md = "    Chapter One\n===\nFirst."
		val result = preview(md, sourceName = "story")
		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `Bold-only lines split a document that has no markdown headings`() {
		val md = """
			**Chapter One**

			First.

			**Chapter Two**

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(null, result.title)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Underscore bold-only lines are chapters too`() {
		val md = "__Chapter One__\nFirst.\n__Chapter Two__\nSecond."
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Bold chapters below a heading title keep the title out of the scenes`() {
		val md = """
			# My Novel

			**Chapter One**

			First.

			**Chapter Two**

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals("My Novel", result.title)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Bold chapters can be wrapped in chapter groups`() {
		val md = "**Chapter One**\nFirst.\n**Chapter Two**\nSecond."
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto, groups = true)
		assertEquals(2, result.items.size)
		val group = result.items[0] as PreviewItem.Group
		assertEquals("Chapter One", group.name)
		assertEquals(listOf("Chapter One"), group.scenes.map { it.name })
	}

	@Test
	fun `Bold text with prose on the same line is not a chapter`() {
		val md = """
			**Chapter One**

			**She** ran, and the world went quiet.

			**Chapter Two**

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(2, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertTrue(scene.markdown.contains("**She** ran"))
	}

	@Test
	fun `A long bold-only line is prose not a chapter`() {
		val md = """
			**Chapter One**

			**And then she said all of the many things she had been holding back for years and years.**

			**Chapter Two**

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertTrue((result.items[0] as PreviewItem.Scene).markdown.contains("**And then she said"))
	}

	@Test
	fun `A lone bold-only line is prose not a chapter`() {
		val md = """
			# Chapter One
			First.

			**A bolded aside**

			More.

			# Chapter Two
			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)
		assertEquals(2, result.items.size)
		val scene = result.items[0] as PreviewItem.Scene
		assertEquals("Chapter One", scene.name)
		assertTrue(scene.markdown.contains("**A bolded aside**"))
	}

	@Test
	fun `Explicitly choosing H1 does not fall back to bold lines`() {
		val md = "**Chapter One**\nFirst.\n**Chapter Two**\nSecond."
		val result = preview(md, strategy = MarkdownSplitStrategy.H1, sourceName = "story")
		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `Pattern strategy splits on lines matching the user regex`() {
		val md = """
			Chapter One

			First.

			Chapter Two

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern)
		assertEquals(2, result.items.size)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Pattern strategy sees through bold and heading markers`() {
		val md = """
			**Chapter One**

			First.

			## Chapter Two

			Second.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern)
		assertEquals(listOf("Chapter One", "Chapter Two"), result.items.map { it.name })
	}

	@Test
	fun `Pattern strategy ignores lines the regex does not match`() {
		val md = """
			Scene Break

			First.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern, sourceName = "story")
		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `Pattern strategy with an invalid regex falls back to a single scene`() {
		val md = "Chapter One\n\nFirst."
		val result = preview(
			md,
			strategy = MarkdownSplitStrategy.Pattern,
			pattern = "[",
			sourceName = "story",
		)
		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `Pattern strategy keeps a Setext underline out of the scene body`() {
		val md = """
			Chapter One
			===========
			First.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern)
		assertEquals(1, result.items.size)
		assertEquals("Chapter One", result.items[0].name)
		assertEquals("First.", (result.items[0] as PreviewItem.Scene).markdown)
	}

	@Test
	fun `Pattern strategy with a blank pattern falls back to a single scene`() {
		val md = "Chapter One\n\nFirst."
		val result = preview(
			md,
			strategy = MarkdownSplitStrategy.Pattern,
			pattern = "",
			sourceName = "story",
		)
		assertEquals(1, result.items.size)
		assertEquals("story", result.items[0].name)
	}

	@Test
	fun `Pattern strategy overrides markdown headings`() {
		val md = """
			# Foreword
			Front matter prose.
			# Chapter One
			First.
		""".trimIndent()
		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern)
		assertEquals(2, result.items.size)
		assertEquals("Untitled", result.items[0].name)
		assertEquals("Chapter One", result.items[1].name)
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
