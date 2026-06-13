package com.darkrockstudios.apps.hammer.review

/**
 * The reviewer editor works in plain-text character offsets within paragraphs.
 * A snapshot is split into paragraphs by newline so the split is lossless
 * (`split('\n')` then `join('\n')` round-trips exactly, regardless of whether
 * the source separates paragraphs with one newline or a blank line). The
 * browser indexes the same way, so a stored `(paragraph, start, end)` anchor
 * means the same thing on both sides.
 */
object ReviewParagraphs {
	fun split(content: String): List<String> = content.split("\n")

	/** The text of paragraph [index], or null if out of range. */
	fun paragraph(content: String, index: Int): String? =
		split(content).getOrNull(index)
}
