package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.markdown_preview_image

private const val ELLIPSIS = "…"

// Paired inline constructs a cut must not fall inside: code, links and images, and emphasis.
private val INLINE_SPAN = Regex(
	"""`[^`\r\n]*`|!?\[[^\]\r\n]*\]\([^)\r\n]*\)|\*\*[^\r\n]*?\*\*|\*[^*\r\n]+\*|__[^\r\n]*?__|_[^_\r\n]+_|~~[^\r\n]*?~~|==[^\r\n]*?=="""
)

/**
 * The Markdown a card shows as a preview of [markdown]. Each project image becomes its alt text in
 * italics ([imagePlaceholder] when the alt text is empty), so a card never renders a full image.
 * The result is trimmed: a trailing newline would render as a blank gap at the bottom of a card.
 * When the result is longer than [maxLength] it is cut at whitespace, outside any inline construct
 * where possible, and ends in an ellipsis, never exceeding [maxLength].
 */
fun markdownPreview(markdown: String, maxLength: Int?, imagePlaceholder: String): String {
	val text = ImageReferenceSyntax.replaceImages(markdown) { alt ->
		"*${alt.trim().ifEmpty { imagePlaceholder }}*"
	}.trim()
	if (maxLength == null || text.length <= maxLength) return text
	return truncate(text, maxLength)
}

/** [markdownPreview] with the localized image placeholder, recomputed only when its inputs change. */
@Composable
fun rememberMarkdownPreview(markdown: String, maxLength: Int? = null): String {
	val placeholder = Res.string.markdown_preview_image.get()
	return remember(markdown, maxLength, placeholder) { markdownPreview(markdown, maxLength, placeholder) }
}

private fun truncate(text: String, maxLength: Int): String {
	val limit = (maxLength - ELLIPSIS.length).coerceAtLeast(0)
	val spans = INLINE_SPAN.findAll(text).map { it.range }.takeWhile { it.first < limit }.toList()
	// Cutting at index i keeps text[0, i) and drops the whitespace at i.
	val cut = (limit downTo 1).firstOrNull { i -> text[i].isWhitespace() && spans.none { i in it } }
		?: (limit downTo 1).firstOrNull { text[it].isWhitespace() }
		?: limit
	return text.substring(0, cut).trimEnd() + ELLIPSIS
}
