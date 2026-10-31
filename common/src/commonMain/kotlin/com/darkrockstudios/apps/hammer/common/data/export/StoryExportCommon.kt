package com.darkrockstudios.apps.hammer.common.data.export

/**
 * Localized strings that appear in the exported document body. Resolved once by the export use case
 * (which has the suspending [com.darkrockstudios.apps.hammer.common.util.StrRes] context) and passed
 * into each renderer, since the renderers themselves are not localization-aware.
 *
 * @param contentsTitle the heading of the table-of-contents page.
 * @param authorByline the title-page byline (e.g. "by Jane Doe"), or null when there is no author.
 * @param wordCount the manuscript's word count line (e.g. "about 12,500 words").
 * @param endMarker the line that closes a manuscript.
 */
data class ExportStrings(
	val contentsTitle: String,
	val authorByline: String?,
	val wordCount: String,
	val endMarker: String,
)

// Layout vocabulary. [chapterTitle] serves every exporter; the rest serves the RTF exporter's book
// layout.

/** Monospace font face of the RTF exporter. */
internal const val EXPORT_MONO_FONT = "Consolas"

/** Heading 1..6 sizes in half-points, for the RTF exporter. */
internal val HEADING_HALF_POINTS = listOf(48, 36, 32, 28, 26, 24)

/** The chapter title as it appears in headings and the Contents page. */
internal fun chapterTitle(index: Int, chapter: StoryChapter, numbered: Boolean): String =
	if (numbered) "${index + 1}. ${chapter.name}" else chapter.name

/** Bookmark / anchor name for the chapter at [index], the target of its Contents link. */
internal fun chapterBookmark(index: Int): String = "chapter${index + 1}"

// Shared markdown text helpers used when turning the parsed prose model into export output.

/** Splits a code block's text into lines, trimming leading and trailing blank lines. */
internal fun codeBlockLines(code: String): List<String> =
	code.split("\n").dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
