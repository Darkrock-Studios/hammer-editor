package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.HorizontalRuleSpanStyle
import com.darkrockstudios.texteditor.richstyle.ImageBlockSpanStyle

/** Private Use Area, so they can never collide with anything a writer types. */
private val HR_MARKER = Char(0xE000)
private val IMAGE_MARKER = Char(0xE001)

/**
 * Horizontal rules and code fences live in the editor's rich span manager, not in the
 * AnnotatedString, so scene content has to travel as markdown and be imported.
 * Round-tripping it as an AnnotatedString silently drops every block. Images need an
 * ImageProvider the editors don't supply, so they import as literal text.
 */
fun loadSceneContent(markdown: MarkdownExtension, sceneContent: SceneContent?) {
	loadSceneContent(markdown, sceneContentMarkdown(sceneContent))
}

fun loadSceneContent(markdown: MarkdownExtension, sceneMarkdown: String) {
	markdown.importMarkdown(sceneMarkdown)
}

/**
 * Serializing a buffer that holds a live editor walks its whole document, so callers on
 * the main thread should do this off it first. [MarkdownExtension.exportAsMarkdown] reads
 * a single immutable snapshot and is safe from any thread.
 */
fun sceneContentMarkdown(sceneContent: SceneContent?): String =
	sceneContent?.coerceMarkdown() ?: ""

/**
 * The editor's rendered text with block placeholders swapped for distinct sentinels.
 * Rules and images both render as a lone space, so a plain diff cannot tell a scene
 * break from a blank line and silently drops one during a draft merge. The swap is
 * length-preserving to keep the result in the editor's coordinate space, which is
 * what the diff highlight spans are drawn in.
 */
fun sceneDiffText(markdown: MarkdownExtension): String {
	val text = markdown.editorState.getAllText().text
	val markers = blockMarkersByLine(markdown)
	if (markers.isEmpty()) return text

	val chars = StringBuilder(text)
	var line = 0
	var lineStart = 0
	while (true) {
		val newline = chars.indexOf("\n", lineStart)
		val end = if (newline == -1) chars.length else newline
		val marker = markers[line]
		// Only a bare placeholder line is a block; anything longer is real prose.
		if (marker != null && end - lineStart == 1) {
			chars[lineStart] = marker
		}
		if (newline == -1) break
		lineStart = newline + 1
		line++
	}
	return chars.toString()
}

private fun blockMarkersByLine(markdown: MarkdownExtension): Map<Int, Char> =
	markdown.editorState.richSpanManager.getAllRichSpans()
		.mapNotNull { span ->
			when (span.style) {
				is HorizontalRuleSpanStyle -> span.range.start.line to HR_MARKER
				is ImageBlockSpanStyle -> span.range.start.line to IMAGE_MARKER
				else -> null
			}
		}
		.toMap()
