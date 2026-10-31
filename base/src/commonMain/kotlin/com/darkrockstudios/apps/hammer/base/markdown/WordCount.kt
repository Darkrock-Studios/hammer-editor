package com.darkrockstudios.apps.hammer.base.markdown

/**
 * Counts the words in [text] by Unicode default word boundaries ([wordSpans]): a word holds at
 * least one letter or digit, so standalone Markdown markers (`---`, `#`, `-`, `>`) are not words.
 * An inline image, alt text and destination alike, is not prose and counts for nothing: the text
 * between images is segmented run by run, so an image also ends the word before it.
 */
fun countWords(text: String): Int = countedWordSpans(text).count()

/**
 * The spans of the words [countWords] counts, as indices into [text]: the [wordSpans] of each run
 * of text between inline images. Lazy and single pass: a whole manuscript is one string, not a
 * word list.
 */
fun countedWordSpans(text: String): Sequence<IntRange> = sequence {
	val length = text.length
	var runStart = 0
	var bang = text.indexOf('!')
	while (bang >= 0) {
		val imageEnd = if (bang == 0 || text[bang - 1] != '\\') {
			inlineImageEnd(bang, length) { text[it] }
		} else {
			-1
		}
		if (imageEnd > 0) {
			yieldAll(wordSpansIn(text, runStart, bang))
			runStart = imageEnd
			bang = text.indexOf('!', imageEnd)
		} else {
			bang = text.indexOf('!', bang + 1)
		}
	}
	yieldAll(wordSpansIn(text, runStart, length))
}

private fun wordSpansIn(text: String, start: Int, end: Int): Sequence<IntRange> = when {
	start >= end -> emptySequence()
	start == 0 && end == text.length -> wordSpans(text)
	else -> wordSpans(TextSlice(text, start, end)).map { (it.first + start)..(it.last + start) }
}

/** A view of `text[start until end]`; `String.subSequence` would copy the run. */
private class TextSlice(private val text: String, private val start: Int, private val end: Int) : CharSequence {
	override val length: Int get() = end - start

	override fun get(index: Int): Char = text[start + index]

	override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
		TextSlice(text, start + startIndex, start + endIndex)

	override fun toString(): String = text.substring(start, end)
}
