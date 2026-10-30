package com.darkrockstudios.apps.hammer.base.markdown

/**
 * Counts the words in [text] by Unicode default word boundaries ([wordSpans]): a word holds at
 * least one letter or digit, so standalone Markdown markers (`---`, `#`, `-`, `>`) are not words.
 * An inline image, alt text and destination alike, is not prose and counts for nothing: the text
 * between images is segmented run by run, so an image also ends the word before it. Single pass:
 * a whole manuscript is one string, not a word list.
 */
fun countWords(text: String): Int {
	val length = text.length
	var count = 0
	var runStart = 0
	var bang = text.indexOf('!')
	while (bang >= 0) {
		val imageEnd = if (bang == 0 || text[bang - 1] != '\\') {
			inlineImageEnd(bang, length) { text[it] }
		} else {
			-1
		}
		if (imageEnd > 0) {
			count += countWordsIn(text, runStart, bang)
			runStart = imageEnd
			bang = text.indexOf('!', imageEnd)
		} else {
			bang = text.indexOf('!', bang + 1)
		}
	}
	return count + countWordsIn(text, runStart, length)
}

private fun countWordsIn(text: String, start: Int, end: Int): Int = when {
	start >= end -> 0
	start == 0 && end == text.length -> wordSpans(text).count()
	else -> wordSpans(TextSlice(text, start, end)).count()
}

/** A view of `text[start until end]`; `String.subSequence` would copy the run. */
private class TextSlice(private val text: String, private val start: Int, private val end: Int) : CharSequence {
	override val length: Int get() = end - start

	override fun get(index: Int): Char = text[start + index]

	override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
		TextSlice(text, start + startIndex, start + endIndex)

	override fun toString(): String = text.substring(start, end)
}
