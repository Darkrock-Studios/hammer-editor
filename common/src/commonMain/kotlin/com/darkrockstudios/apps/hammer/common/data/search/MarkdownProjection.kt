package com.darkrockstudios.apps.hammer.common.data.search

/**
 * Flattens stored Markdown into the prose a reader sees. Backslash escapes resolve to their literal
 * character (`well\-known` becomes `well-known`), paired emphasis and code delimiters are dropped
 * (`**Chapter** One` becomes `Chapter One`), and leading blockquote, heading and bullet markers are
 * removed from each line. An inline image becomes its alt text (`![Cat](hammer://image/3)` becomes
 * `Cat`).
 *
 * Delimiters pair only within a paragraph, and only where CommonMark's flanking rules allow, so
 * literal markers survive: `user_name`, `2 * 3`, a bare `***` divider and a stray apostrophe-backtick
 * are all left intact.
 *
 * Link syntax is left as written. Punctuation is tested against ASCII only, and escapes
 * resolve inside code spans as well as outside them.
 *
 * Scanning a whole project repeatedly wants [MarkdownProjector] instead: this convenience builds a
 * workspace per call, where the projector reuses one across every document in a scan.
 */
fun projectMarkdownToPlainText(markdown: String): String {
	if (!containsMarkdownSyntax(markdown)) return markdown
	val projector = MarkdownProjector(markdown.length)
	projector.project(markdown)
	return projector.projected()
}

/**
 * The prose title for [markdown]: the first non-blank line of its projection. Falls back to the raw
 * first line when the projection of it is blank, so a marker-only line still yields a title rather
 * than reading as an empty document.
 */
fun markdownTitleLine(markdown: String): String {
	if (!containsMarkdownSyntax(markdown)) {
		return markdown.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
	}
	val projector = MarkdownProjector(markdown.length)
	projector.project(markdown)
	val title = projector.firstNonBlankLine()
	if (title.isNotEmpty()) return title
	return markdown.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

/** [markdownTitleLine] cut to [maxLength] characters, ending in an ellipsis when it was longer. */
fun markdownTitle(markdown: String, maxLength: Int): String {
	val line = markdownTitleLine(markdown)
	return if (line.length <= maxLength) line else line.take(maxLength).trimEnd() + "…"
}

/**
 * True when [text] holds a character the projection can remove. Text without one projects to itself,
 * so the scan can be skipped entirely.
 */
internal fun containsMarkdownSyntax(text: CharSequence): Boolean {
	var i = 0
	while (i < text.length) {
		val c = text[i]
		if (c == '*' || c == '_' || c == '`' ||
			c == '\\' || c == '#' || c == '>' || c == '-' || c == '+' ||
			c == '!' && i + 1 < text.length && text[i + 1] == '['
		) {
			return true
		}
		i++
	}
	return false
}
