package com.darkrockstudios.apps.hammer.common.data.search

import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax

/**
 * Flattens stored Markdown into the prose a reader sees. Backslash escapes resolve to their literal
 * character (`well\-known` becomes `well-known`), paired emphasis and code delimiters are dropped
 * (`**Chapter** One` becomes `Chapter One`), and leading blockquote, heading and bullet markers are
 * removed from each line. A project image becomes the name [imageName] gives its id, or nothing when
 * it gives none: its alt text carries no meaning. Any other inline image becomes its alt text.
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
fun projectMarkdownToPlainText(markdown: String, imageName: (imageId: Int) -> String? = { null }): String {
	if (!containsMarkdownSyntax(markdown)) return markdown
	val projector = MarkdownProjector(markdown.length, imageName)
	projector.project(markdown)
	return projector.projected()
}

/**
 * The prose title for [markdown]: the first non-blank line of its projection, with project images
 * named by [imageName]. Falls back to the raw first line when the projection is blank, so a
 * marker-only line still yields a title rather than reading as an empty document; a line holding a
 * project image is never that fallback, as its Markdown means nothing to a reader.
 */
fun markdownTitleLine(markdown: String, imageName: (imageId: Int) -> String? = { null }): String {
	if (!containsMarkdownSyntax(markdown)) return rawTitleLine(markdown)
	val projector = MarkdownProjector(markdown.length, imageName)
	projector.project(markdown)
	return projector.firstNonBlankLine().ifEmpty { rawTitleLine(markdown) }
}

/** The first non-blank line of [markdown] as written, or "" when it holds a project image. */
internal fun rawTitleLine(markdown: String): String {
	val line = markdown.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
	return if (ImageReferenceSyntax.findIds(line).isEmpty()) line else ""
}

/** [markdownTitleLine] cut to [maxLength] characters, ending in an ellipsis when it was longer. */
fun markdownTitle(markdown: String, maxLength: Int, imageName: (imageId: Int) -> String? = { null }): String {
	val line = markdownTitleLine(markdown, imageName)
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
