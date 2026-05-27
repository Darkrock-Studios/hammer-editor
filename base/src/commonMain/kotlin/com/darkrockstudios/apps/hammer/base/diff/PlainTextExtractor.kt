package com.darkrockstudios.apps.hammer.base.diff

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * A run of plain text whose offsets map 1:1 to a contiguous source range:
 *   sourceOffset = sourceStart + (plainOffset - plainStart)
 *
 * Segments are produced in order and never overlap. The gaps between them in source space are
 * exactly the markdown syntax characters we elided (e.g. `**`, `#`, list markers).
 */
internal data class PlainSegment(
	val plainStart: Int,
	val plainEnd: Int,
	val sourceStart: Int,
	val sourceEnd: Int,
)

internal data class PlainTextResult(
	val plain: String,
	val segments: List<PlainSegment>,
) {
	/**
	 * Map a half-open plain-text range to the smallest source range that covers it.
	 *
	 * If the plain range falls within a single segment, the returned source range maps it
	 * exactly. If it spans multiple segments, the result includes the syntax characters
	 * between them — acceptable for highlighting because the user sees the highlight cover
	 * those formatting marks too, which is honest about what changed.
	 */
	fun plainRangeToSource(plainStart: Int, plainEnd: Int): SourceRange {
		if (segments.isEmpty()) return SourceRange.EMPTY
		if (plainEnd <= plainStart) {
			val src = plainOffsetToSource(plainStart, preferEnd = false)
			return SourceRange(src, src)
		}
		val startSource = plainOffsetToSource(plainStart, preferEnd = false)
		val endSource = plainOffsetToSource(plainEnd, preferEnd = true)
		return SourceRange(start = startSource, endExclusive = endSource.coerceAtLeast(startSource))
	}

	/**
	 * Map a plain-text offset to a source offset.
	 *
	 * Plain offsets at segment boundaries are ambiguous (the same plain position can sit at the
	 * end of one segment or the start of the next). [preferEnd] picks: an INSERTED span's right
	 * edge wants the end-of-previous-segment source position so the highlight doesn't run into
	 * the following whitespace; the left edge wants start-of-next.
	 */
	fun plainOffsetToSource(plainOffset: Int, preferEnd: Boolean = false): Int {
		if (segments.isEmpty()) return 0
		if (plainOffset <= segments.first().plainStart) return segments.first().sourceStart
		val last = segments.last()
		if (plainOffset >= last.plainEnd) return last.sourceEnd
		// Binary search for the segment containing plainOffset.
		var lo = 0
		var hi = segments.size - 1
		while (lo <= hi) {
			val mid = (lo + hi) ushr 1
			val seg = segments[mid]
			when {
				plainOffset < seg.plainStart -> hi = mid - 1
				plainOffset > seg.plainEnd -> lo = mid + 1
				else -> {
					// Inside [plainStart..plainEnd]. Boundary cases: pick neighbor per preference.
					return when {
						plainOffset == seg.plainStart && !preferEnd -> seg.sourceStart
						plainOffset == seg.plainEnd && preferEnd -> seg.sourceEnd
						plainOffset == seg.plainStart && preferEnd && mid > 0 -> segments[mid - 1].sourceEnd
						plainOffset == seg.plainEnd && !preferEnd && mid < segments.lastIndex -> segments[mid + 1].sourceStart
						else -> seg.sourceStart + (plainOffset - seg.plainStart)
					}
				}
			}
		}
		// Plain offset lives in a gap (shouldn't happen since segments include whitespace, but
		// be defensive): clamp to the nearest segment boundary.
		val nearest = segments.getOrNull(lo) ?: segments.last()
		return if (plainOffset < nearest.plainStart) nearest.sourceStart else nearest.sourceEnd
	}
}

/**
 * Strip markdown syntax from [markdown] and return the resulting plain text along with the
 * offset map back to the original source.
 *
 * Handles common prose constructs: paragraphs, ATX/Setext headers, emphasis/strong, lists,
 * blockquotes. Links and images contribute their visible text only. Code blocks contribute
 * their inner content.
 *
 * Known limitations: HTML blocks are dropped entirely. Reference link definitions are dropped.
 * These are exceedingly rare in fiction drafts.
 */
internal fun extractPlainText(markdown: String): PlainTextResult {
	if (markdown.isEmpty()) return PlainTextResult("", emptyList())
	val tree = parser.buildMarkdownTreeFromString(markdown)
	val plain = StringBuilder(markdown.length)
	val segments = ArrayList<PlainSegment>()
	walk(tree, markdown, plain, segments)
	return PlainTextResult(plain.toString(), segments)
}

private val flavour = CommonMarkFlavourDescriptor()
private val parser = MarkdownParser(flavour)

/** Leaf token types that exist only as markdown syntax — never emit them as plain text. */
private val SUPPRESSED_TOKENS: Set<Any> = setOf(
	MarkdownTokenTypes.EMPH,
	MarkdownTokenTypes.BACKTICK,
	MarkdownTokenTypes.ESCAPED_BACKTICKS,
	MarkdownTokenTypes.ATX_HEADER,
	MarkdownTokenTypes.SETEXT_1,
	MarkdownTokenTypes.SETEXT_2,
	MarkdownTokenTypes.HORIZONTAL_RULE,
	MarkdownTokenTypes.LIST_BULLET,
	MarkdownTokenTypes.LIST_NUMBER,
	MarkdownTokenTypes.BLOCK_QUOTE,
	MarkdownTokenTypes.CODE_FENCE_START,
	MarkdownTokenTypes.CODE_FENCE_END,
	MarkdownTokenTypes.FENCE_LANG,
	MarkdownTokenTypes.HTML_BLOCK_CONTENT,
	MarkdownTokenTypes.HTML_TAG,
)

/** Container element types whose subtree should be skipped entirely. */
private val SUPPRESSED_ELEMENTS: Set<Any> = setOf(
	MarkdownElementTypes.LINK_DESTINATION,
	MarkdownElementTypes.LINK_TITLE,
	MarkdownElementTypes.LINK_LABEL,
	MarkdownElementTypes.LINK_DEFINITION,
	MarkdownElementTypes.IMAGE,
	MarkdownElementTypes.HTML_BLOCK,
)

private fun walk(
	node: ASTNode,
	source: String,
	plain: StringBuilder,
	segments: MutableList<PlainSegment>,
) {
	val type = node.type
	if (type in SUPPRESSED_ELEMENTS) return

	when (type) {
		MarkdownElementTypes.INLINE_LINK,
		MarkdownElementTypes.FULL_REFERENCE_LINK,
		MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
			val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
			if (linkText != null) {
				for (child in linkText.children) {
					if (child.type == MarkdownTokenTypes.LBRACKET || child.type == MarkdownTokenTypes.RBRACKET) continue
					walk(child, source, plain, segments)
				}
			}
			return
		}
	}

	if (node.children.isEmpty()) {
		if (type in SUPPRESSED_TOKENS) return
		val sStart = node.startOffset
		val sEnd = node.endOffset
		if (sEnd <= sStart) return
		appendSegment(plain, segments, source, sStart, sEnd)
		return
	}

	for (child in node.children) walk(child, source, plain, segments)
}

private fun appendSegment(
	plain: StringBuilder,
	segments: MutableList<PlainSegment>,
	source: String,
	sourceStart: Int,
	sourceEnd: Int,
) {
	val pStart = plain.length
	plain.append(source, sourceStart, sourceEnd)
	val pEnd = plain.length
	if (segments.isNotEmpty()) {
		val last = segments.last()
		if (last.plainEnd == pStart && last.sourceEnd == sourceStart) {
			segments[segments.lastIndex] = last.copy(plainEnd = pEnd, sourceEnd = sourceEnd)
			return
		}
	}
	segments.add(PlainSegment(pStart, pEnd, sourceStart, sourceEnd))
}
