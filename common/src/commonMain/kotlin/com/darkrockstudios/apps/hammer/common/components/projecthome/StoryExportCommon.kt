package com.darkrockstudios.apps.hammer.common.components.projecthome

/**
 * Localized strings that appear in the exported document body. Resolved once by the export use case
 * (which has the suspending [com.darkrockstudios.apps.hammer.common.util.StrRes] context) and passed
 * into each renderer, since the renderers themselves are not localization-aware.
 *
 * @param contentsTitle the heading of the table-of-contents page.
 * @param authorByline the title-page byline (e.g. "by Jane Doe"), or null when there is no author.
 */
data class ExportStrings(
	val contentsTitle: String,
	val authorByline: String?,
)

// Shared layout vocabulary mirrored across the EPUB / DOCX / RTF / PDF exporters. Keeping these in one
// place is what lets the "mirrored" exports actually stay in step when the layout is tuned.

/** Prose and monospace font faces shared by the DOCX and RTF exporters. */
internal const val EXPORT_BODY_FONT = "Georgia"
internal const val EXPORT_MONO_FONT = "Consolas"

/** Heading 1..6 sizes in half-points, shared by the DOCX and RTF exporters. */
internal val HEADING_HALF_POINTS = listOf(48, 36, 32, 28, 26, 24)

/** Bookmark / anchor name for the chapter at [index], the target of its Contents link. */
internal fun chapterBookmark(index: Int): String = "chapter${index + 1}"

// Shared markdown text helpers used when turning the parsed prose model into export output.

/** Splits a code block's text into lines, trimming leading and trailing blank lines. */
internal fun codeBlockLines(code: String): List<String> =
	code.split("\n").dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
