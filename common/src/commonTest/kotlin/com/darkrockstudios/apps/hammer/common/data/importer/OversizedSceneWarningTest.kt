package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OversizedSceneWarningTest {

	private val importer = MarkdownStoryImporter()

	private fun words(count: Int): String = "word ".repeat(count)

	private fun preview(
		content: String,
		groups: Boolean = false,
		strategy: MarkdownSplitStrategy = MarkdownSplitStrategy.H1,
	) = importer.preview(
		sourceName = "story",
		content = content.encodeToByteArray(),
		options = ImportOptions(markdownSplitStrategy = strategy, createChapterGroups = groups),
	)

	@Test
	fun `A whole manuscript collapsed into one scene is flagged`() {
		val result = preview(words(LARGE_SCENE_WORD_COUNT + 500))

		assertEquals(1, result.totalScenes)
		val flagged = result.oversizedScenes.single()
		assertEquals(LARGE_SCENE_WORD_COUNT + 500, flagged.wordCount)
	}

	@Test
	fun `Chapters that split correctly are not flagged`() {
		val md = (1..5).joinToString("\n") { "# Chapter $it\n${words(2_000)}" }

		val result = preview(md)

		assertEquals(5, result.totalScenes)
		assertTrue(result.oversizedScenes.isEmpty())
	}

	@Test
	fun `A scene exactly at the threshold is flagged`() {
		val result = preview(words(LARGE_SCENE_WORD_COUNT))

		assertEquals(LARGE_SCENE_WORD_COUNT, result.oversizedScenes.single().wordCount)
	}

	@Test
	fun `A scene one word under the threshold is not flagged`() {
		val result = preview(words(LARGE_SCENE_WORD_COUNT - 1))

		assertTrue(result.oversizedScenes.isEmpty())
	}

	@Test
	fun `Only the oversized chapter of a mixed import is flagged`() {
		val md = buildString {
			appendLine("# Chapter One")
			appendLine(words(500))
			appendLine("# Chapter Two")
			appendLine(words(LARGE_SCENE_WORD_COUNT + 1))
			appendLine("# Chapter Three")
			appendLine(words(500))
		}

		val result = preview(md)

		assertEquals(3, result.totalScenes)
		assertEquals(listOf("Chapter Two"), result.oversizedScenes.map { it.name })
	}

	@Test
	fun `An oversized scene nested in a chapter group is flagged`() {
		val md = "# Chapter One\n${words(LARGE_SCENE_WORD_COUNT + 1)}"

		val result = preview(md, groups = true)

		assertTrue(result.items.single() is PreviewItem.Group)
		assertEquals(listOf("Chapter One"), result.oversizedScenes.map { it.name })
	}

	@Test
	fun `An empty preview flags nothing`() {
		assertTrue(preview("").oversizedScenes.isEmpty())
	}

	@Test
	fun `Scenes split by Setext headings carry their word count`() {
		val md = """
			Chapter One
			===========

			${words(500)}

			Chapter Two
			===========

			${words(LARGE_SCENE_WORD_COUNT + 1)}
		""".trimIndent()

		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)

		assertEquals(2, result.totalScenes)
		assertEquals(listOf("Chapter Two"), result.oversizedScenes.map { it.name })
	}

	@Test
	fun `A Setext title with no chapters collapses to one flagged scene`() {
		val md = "The Long Walk Home\n==================\n\n${words(LARGE_SCENE_WORD_COUNT + 1)}"

		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)

		assertEquals(LARGE_SCENE_WORD_COUNT + 1, result.oversizedScenes.single().wordCount)
	}

	@Test
	fun `Scenes split by a bold chapter title carry their word count`() {
		val md = (1..4).joinToString("\n") { "**Chapter $it**\n${words(3_000)}" } +
			"\n**Chapter Five**\n${words(LARGE_SCENE_WORD_COUNT + 1)}"

		val result = preview(md, strategy = MarkdownSplitStrategy.Auto)

		assertEquals(listOf("Chapter Five"), result.oversizedScenes.map { it.name })
	}

	@Test
	fun `Scenes split by the chapter pattern carry their word count`() {
		val md = "Chapter One\n${words(500)}\nChapter Two\n${words(LARGE_SCENE_WORD_COUNT + 1)}"

		val result = preview(md, strategy = MarkdownSplitStrategy.Pattern)

		assertEquals(listOf("Chapter Two"), result.oversizedScenes.map { it.name })
	}

	@Test
	fun `Word count is derived on decode rather than carried in the serialized form`() {
		val decoded = Json.decodeFromString<PreviewItem.Scene>(
			"""{"name":"Chapter One","markdown":"one two three"}""",
		)

		assertEquals(3, decoded.wordCount)
	}
}
