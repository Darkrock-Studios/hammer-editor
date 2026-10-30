package com.darkrockstudios.apps.hammer.base.markdown

/*
 * Unicode default word boundaries (UAX #29, rules WB1 to WB999) with no dictionary or locale
 * tailoring, over the table in WordBreakProperties.kt. Text is read by code point: a surrogate
 * pair is one code point and a lone surrogate is one code point of class Other. Offsets are
 * in chars.
 */

/**
 * Every word boundary in [text], in order: 0 first and `text.length` last. Empty text yields
 * just 0.
 */
fun wordBoundaries(text: CharSequence): Sequence<Int> = Sequence { BoundaryIterator(text) }

/**
 * The segments between consecutive boundaries that hold at least one letter or digit, as
 * `start until end`. Punctuation, spaces, symbols and emoji between words are not spans.
 */
fun wordSpans(text: CharSequence): Sequence<IntRange> = Sequence { SpanIterator(text) }

/** The text of each of [wordSpans]. */
fun words(text: CharSequence): List<String> =
	wordSpans(text).map { text.substring(it) }.toList()

internal fun wordBreakValue(codePoint: Int): Int =
	if (codePoint < ASCII_VALUES.size) ASCII_VALUES[codePoint] else searchWordBreakRanges(codePoint)

private val ASCII_VALUES = IntArray(0x80) { searchWordBreakRanges(it) }

private fun searchWordBreakRanges(codePoint: Int): Int {
	var low = 0
	var high = WORD_BREAK_RANGES.size / 3 - 1
	while (low <= high) {
		val mid = (low + high) ushr 1
		val base = mid * 3
		when {
			codePoint < WORD_BREAK_RANGES[base] -> high = mid - 1
			codePoint > WORD_BREAK_RANGES[base + 1] -> low = mid + 1
			else -> return WORD_BREAK_RANGES[base + 2]
		}
	}
	return WB_OTHER
}

private fun isWordBearing(value: Int): Boolean {
	if (value and WB_FLAG_LETTER != 0) return true
	return when (value and WB_MASK) {
		WB_ALETTER, WB_HEBREW_LETTER, WB_NUMERIC, WB_KATAKANA -> true
		else -> false
	}
}

private fun isIgnorable(wb: Int) = wb == WB_EXTEND || wb == WB_FORMAT || wb == WB_ZWJ
private fun isNewline(wb: Int) = wb == WB_NEWLINE || wb == WB_CR || wb == WB_LF
private fun isAHLetter(wb: Int) = wb == WB_ALETTER || wb == WB_HEBREW_LETTER
private fun isMidNumLetQ(wb: Int) = wb == WB_MID_NUM_LET || wb == WB_SINGLE_QUOTE

private fun codePointAt(text: CharSequence, index: Int): Int {
	val high = text[index]
	if (high.isHighSurrogate() && index + 1 < text.length) {
		val low = text[index + 1]
		if (low.isLowSurrogate()) {
			return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
		}
	}
	return high.code
}

private fun charCount(codePoint: Int) = if (codePoint >= 0x10000) 2 else 1

/**
 * One forward pass over [text]. [next] returns each boundary in turn, then -1. Holds only the
 * state the rules need: the literal previous class (WB3 to WB4), the previous two classes with
 * Extend, Format and ZWJ skipped (WB4 onward), and the parity of the Regional_Indicator run.
 */
private class WordBreaker(private val text: CharSequence) {
	private var position = 0
	private var started = false
	private var finished = false

	private var previous = WB_OTHER
	private var left = WB_OTHER
	private var beforeLeft = WB_OTHER
	private var oddRegionalIndicators = false

	private var segmentHasWord = false

	/** Whether the segment that ends at the boundary [next] last returned holds a letter or digit. */
	var lastSegmentHasWord = false
		private set

	fun next(): Int {
		if (!started) {
			started = true
			// WB1
			if (text.isNotEmpty()) {
				val codePoint = codePointAt(text, 0)
				val value = wordBreakValue(codePoint)
				previous = value and WB_MASK
				left = previous
				oddRegionalIndicators = left == WB_REGIONAL_INDICATOR
				segmentHasWord = isWordBearing(value)
				position = charCount(codePoint)
			} else {
				finished = true
			}
			return 0
		}
		if (finished) return -1

		while (position < text.length) {
			val at = position
			val codePoint = codePointAt(text, at)
			val value = wordBreakValue(codePoint)
			val right = value and WB_MASK
			position += charCount(codePoint)

			val breaks = isBreak(right, value)
			val wordBearing = isWordBearing(value)
			if (breaks) {
				lastSegmentHasWord = segmentHasWord
				segmentHasWord = wordBearing
			} else if (wordBearing) {
				segmentHasWord = true
			}

			// WB4: an Extend, Format or ZWJ joined to what precedes it is invisible to later rules.
			if (!isIgnorable(right) || isNewline(previous)) {
				oddRegionalIndicators = right == WB_REGIONAL_INDICATOR &&
					!(left == WB_REGIONAL_INDICATOR && oddRegionalIndicators)
				beforeLeft = left
				left = right
			}
			previous = right

			if (breaks) return at
		}

		// WB2
		finished = true
		lastSegmentHasWord = segmentHasWord
		return text.length
	}

	/** Whether there is a boundary before the code point of class [right] and table value [value]; [position] is past it. */
	private fun isBreak(right: Int, value: Int): Boolean {
		if (previous == WB_CR && right == WB_LF) return false // WB3
		if (isNewline(previous)) return true // WB3a
		if (isNewline(right)) return true // WB3b
		if (previous == WB_ZWJ && value and WB_FLAG_EXTENDED_PICTOGRAPHIC != 0) return false // WB3c
		if (previous == WB_WSEG_SPACE && right == WB_WSEG_SPACE) return false // WB3d
		if (isIgnorable(right)) return false // WB4

		if (isAHLetter(left)) {
			if (isAHLetter(right)) return false // WB5
			if ((right == WB_MID_LETTER || isMidNumLetQ(right)) && isAHLetter(lookAhead())) return false // WB6
		}
		if (isAHLetter(beforeLeft) && (left == WB_MID_LETTER || isMidNumLetQ(left)) && isAHLetter(right)) {
			return false // WB7
		}
		if (left == WB_HEBREW_LETTER) {
			if (right == WB_SINGLE_QUOTE) return false // WB7a
			if (right == WB_DOUBLE_QUOTE && lookAhead() == WB_HEBREW_LETTER) return false // WB7b
		}
		if (beforeLeft == WB_HEBREW_LETTER && left == WB_DOUBLE_QUOTE && right == WB_HEBREW_LETTER) {
			return false // WB7c
		}
		if (left == WB_NUMERIC && right == WB_NUMERIC) return false // WB8
		if (isAHLetter(left) && right == WB_NUMERIC) return false // WB9
		if (left == WB_NUMERIC && isAHLetter(right)) return false // WB10
		if (beforeLeft == WB_NUMERIC && (left == WB_MID_NUM || isMidNumLetQ(left)) && right == WB_NUMERIC) {
			return false // WB11
		}
		if (left == WB_NUMERIC && (right == WB_MID_NUM || isMidNumLetQ(right)) && lookAhead() == WB_NUMERIC) {
			return false // WB12
		}
		if (left == WB_KATAKANA && right == WB_KATAKANA) return false // WB13
		if (right == WB_EXTEND_NUM_LET &&
			(isAHLetter(left) || left == WB_NUMERIC || left == WB_KATAKANA || left == WB_EXTEND_NUM_LET)
		) {
			return false // WB13a
		}
		if (left == WB_EXTEND_NUM_LET && (isAHLetter(right) || right == WB_NUMERIC || right == WB_KATAKANA)) {
			return false // WB13b
		}
		if (left == WB_REGIONAL_INDICATOR && right == WB_REGIONAL_INDICATOR && oddRegionalIndicators) {
			return false // WB15, WB16
		}
		return true // WB999
	}

	/** The class of the first code point after the current one that is not Extend, Format or ZWJ, or -1 at the end. */
	private fun lookAhead(): Int {
		var index = position
		while (index < text.length) {
			val codePoint = codePointAt(text, index)
			val wb = wordBreakValue(codePoint) and WB_MASK
			if (!isIgnorable(wb)) return wb
			index += charCount(codePoint)
		}
		return -1
	}
}

private class BoundaryIterator(text: CharSequence) : Iterator<Int> {
	private val breaker = WordBreaker(text)
	private var upcoming = breaker.next()

	override fun hasNext() = upcoming >= 0

	override fun next(): Int {
		val boundary = upcoming
		if (boundary < 0) throw NoSuchElementException()
		upcoming = breaker.next()
		return boundary
	}
}

private class SpanIterator(text: CharSequence) : Iterator<IntRange> {
	private val breaker = WordBreaker(text)
	private var start = breaker.next()
	private var upcoming = advance()

	private fun advance(): IntRange? {
		while (true) {
			val end = breaker.next()
			if (end < 0) return null
			val segmentStart = start
			start = end
			if (breaker.lastSegmentHasWord) return segmentStart until end
		}
	}

	override fun hasNext() = upcoming != null

	override fun next(): IntRange {
		val span = upcoming ?: throw NoSuchElementException()
		upcoming = advance()
		return span
	}
}
