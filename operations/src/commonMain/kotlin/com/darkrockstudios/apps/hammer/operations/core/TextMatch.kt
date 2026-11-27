package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjector

// Exact matching of a term in scene text, for text.count and text.find.

/**
 * A projector for [normalizeText], to reuse across every scene in a scan. Inline images project to
 * nothing, alt text included, as they count for no words.
 */
internal fun sceneTextProjector() = MarkdownProjector(imageAltText = false)

/**
 * Scene [markdown] as terms are matched against it: projected to the prose a reader sees, then with
 * typographic quotes and apostrophes made straight and each run of whitespace made one space. A run
 * holding a paragraph break (a blank line) becomes one line break instead, so each paragraph is one
 * line. Whitespace at either end is dropped.
 */
internal fun normalizeText(markdown: String, projector: MarkdownProjector = sceneTextProjector()): String {
	val length = projector.project(markdown)
	return collapse(length, paragraphs = true) { projector[it] }
}

/** [term] as it is matched: [normalizeText] without the projection, so its marks are taken as written, on one line. */
internal fun normalizeTerm(term: String): String = collapse(term.length, paragraphs = false) { term[it] }

private inline fun collapse(length: Int, paragraphs: Boolean, charAt: (Int) -> Char): String {
	val out = StringBuilder(length)
	// Whitespace waiting for the next character it separates, with the line breaks in it.
	var space = false
	var breaks = 0
	for (i in 0 until length) {
		val c = charAt(i)
		if (c.isWhitespace()) {
			space = true
			if (c == '\n') breaks++
			continue
		}
		if (space && out.isNotEmpty()) out.append(if (paragraphs && breaks >= 2) '\n' else ' ')
		space = false
		breaks = 0
		out.append(
			when (c) {
				'‘', '’' -> '\''
				'“', '”' -> '"'
				else -> c
			},
		)
	}
	return out.toString()
}

/**
 * Finds [term] in text from [normalizeText]. The term is normalised by [normalizeTerm], with no
 * paragraph breaks, so a match never spans two paragraphs. Unless [caseSensitive], case is ignored a
 * character at a time. With [wholeWord], a match may not have a letter or digit directly before it when the term
 * starts with one, nor directly after it when the term ends with one.
 */
internal class TextMatcher(term: String, private val caseSensitive: Boolean, wholeWord: Boolean) {
	val term = normalizeTerm(term)

	// this.term: the bare name is the constructor's raw term.
	private val boundedBefore = wholeWord && this.term.isNotEmpty() && isWordChar(this.term.first())
	private val boundedAfter = wholeWord && this.term.isNotEmpty() && isWordChar(this.term.last())

	/** Where each match in [text] starts, left to right; matches don't overlap. Each is [term]'s length. */
	fun matches(text: String): List<Int> {
		val starts = mutableListOf<Int>()
		if (term.isEmpty()) return starts
		var from = 0
		while (true) {
			val at = text.indexOf(term, from, ignoreCase = !caseSensitive)
			if (at < 0) return starts
			val end = at + term.length
			if ((boundedBefore && at > 0 && isWordChar(text[at - 1])) || (boundedAfter && end < text.length && isWordChar(text[end]))) {
				from = at + 1
			} else {
				starts += at
				from = end
			}
		}
	}

	fun count(text: String): Int = matches(text).size
}

/**
 * The text around [start] until [end] in text from [normalizeText]: up to about [reach] characters
 * either side, within the paragraph, cut at a space where it is cut, with an ellipsis there.
 */
internal fun context(text: String, start: Int, end: Int, reach: Int = CONTEXT_REACH): String {
	val paragraphStart = text.lastIndexOf('\n', start - 1) + 1
	val paragraphEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
	val from = if (start - paragraphStart <= reach) {
		paragraphStart
	} else {
		// With no space to cut at, it is cut where the reach ends.
		val space = text.indexOf(' ', start - reach)
		if (space in 0 until start) space + 1 else (start - reach).let { if (text[it].isLowSurrogate()) it + 1 else it }
	}
	val to = if (paragraphEnd - end <= reach) {
		paragraphEnd
	} else {
		val space = text.lastIndexOf(' ', end + reach)
		if (space >= end) space else (end + reach).let { if (text[it - 1].isHighSurrogate()) it - 1 else it }
	}
	return buildString {
		if (from > paragraphStart) append(ELLIPSIS)
		append(text, from, to)
		if (to < paragraphEnd) append(ELLIPSIS)
	}
}

/** A letter or digit in any script, or a combining mark on one. */
private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c.category in MARKS

private val MARKS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)


internal const val CONTEXT_REACH = 80
private const val ELLIPSIS = '…'
