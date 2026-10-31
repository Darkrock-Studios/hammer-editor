package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.LocalImageNames
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.markdown_preview_image

private const val ELLIPSIS = "…"

// Paired inline constructs a cut must not fall inside: code, links and images, and emphasis.
private val INLINE_SPAN = Regex(
	"""`[^`\r\n]*`|!?\[[^\]\r\n]*\]\([^)\r\n]*\)|\*\*[^\r\n]*?\*\*|\*[^*\r\n]+\*|__[^\r\n]*?__|_[^_\r\n]+_|~~[^\r\n]*?~~|==[^\r\n]*?=="""
)

// Characters that would start inline Markdown, or an entity, inside an image's italic label.
private val NAME_SPECIALS = Regex("""[\\`*_\[\]~<=&]""")

/**
 * The Markdown a card shows as a preview of [markdown]. Each project image becomes, in italics, the
 * name [imageName] gives its id, else [imagePlaceholder], so a card never renders a full image; its
 * alt text carries no meaning and is never shown. The result is trimmed: a trailing newline would
 * render as a blank gap at the bottom of a card. When the result is longer than [maxLength] it is cut
 * at whitespace, outside any inline construct where possible, and ends in an ellipsis, never
 * exceeding [maxLength].
 */
fun markdownPreview(
	markdown: String,
	maxLength: Int?,
	imagePlaceholder: String,
	imageName: (imageId: Int) -> String? = { null },
): String {
	val text = ImageReferenceSyntax.replaceImages(markdown) { imageId, _ ->
		val label = imageName(imageId)?.trim()?.ifEmpty { null } ?: imagePlaceholder
		"*${label.replace(NAME_SPECIALS) { "\\${it.value}" }}*"
	}.trim()
	if (maxLength == null || text.length <= maxLength) return text
	return truncate(text, maxLength)
}

/**
 * [markdownPreview] with the localized image placeholder and the [LocalImageNames], recomputed only
 * when its inputs change.
 */
@Composable
fun rememberMarkdownPreview(markdown: String, maxLength: Int? = null): String {
	val placeholder = Res.string.markdown_preview_image.get()
	val imageNames = LocalImageNames.current
	return remember(markdown, maxLength, placeholder, imageNames) {
		markdownPreview(markdown, maxLength, placeholder, imageNames)
	}
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
