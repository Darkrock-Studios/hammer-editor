package com.darkrockstudios.apps.hammer.operations.core

// Exact matching of a term in scene text, for text.count and text.find. Hand rolled rather than a
// markdown parse, so a term is normalised exactly as the text is, whatever marks it holds.

/**
 * [text] as terms are matched against it: the marks Hammer writes around styled text dropped, a
 * backslash escape read as the character it escapes, typographic quotes and apostrophes made straight,
 * and each run of whitespace made one space. With [paragraphs], a run holding a paragraph break (a blank
 * line) becomes one line break instead, so each paragraph is one line. Whitespace at either end is
 * dropped.
 */
internal fun normalizeText(text: String, paragraphs: Boolean = true): String {
	val out = StringBuilder(text.length)
	// Whitespace waiting for the next character it separates, with the line breaks in it; marks between
	// don't end the run.
	var space = false
	var breaks = 0
	var i = 0
	while (i < text.length) {
		val mark = markLength(text, i)
		if (mark > 0) {
			i += mark
			continue
		}
		var c = text[i]
		if (c.isWhitespace()) {
			space = true
			if (c == '\n') breaks++
			i++
			continue
		}
		if (c == '\\' && i + 1 < text.length && text[i + 1] in ASCII_PUNCTUATION) {
			c = text[++i]
			// An asterisk in a term can't be told from emphasis, so a literal one is dropped too.
			if (c == '*') {
				i++
				continue
			}
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
		i++
	}
	return out.toString()
}

/**
 * How long the styling mark at [at] is, or 0 when none starts there: emphasis asterisks, the `~~` of
 * struck text and `==` of highlighted text (exactly two, as a longer run is prose), and an inline
 * styling tag.
 */
private fun markLength(text: String, at: Int): Int {
	val c = text[at]
	return when (c) {
		'*' -> 1
		'~', '=' -> {
			val paired = text.getOrNull(at + 1) == c && text.getOrNull(at - 1) != c && text.getOrNull(at + 2) != c
			if (paired) 2 else 0
		}
		'<' -> STYLE_TAGS.firstOrNull { text.startsWith(it, at, ignoreCase = true) }?.length ?: 0
		else -> 0
	}
}

/**
 * Finds [term] in text from [normalizeText]. The term is normalised the same way, with no paragraph
 * breaks, so a match never spans two paragraphs. Unless [caseSensitive], case is ignored a character at
 * a time. With [wholeWord], a match may not have a letter or digit directly before it when the term
 * starts with one, nor directly after it when the term ends with one.
 */
internal class TextMatcher(term: String, private val caseSensitive: Boolean, wholeWord: Boolean) {
	val term = normalizeText(term, paragraphs = false)

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

private const val ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
private val STYLE_TAGS = listOf("<u>", "</u>", "<em>", "</em>", "<strong>", "</strong>", "<del>", "</del>")

internal const val CONTEXT_REACH = 80
private const val ELLIPSIS = '…'
