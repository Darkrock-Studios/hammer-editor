package com.darkrockstudios.apps.hammer.base.markdown

/**
 * Counts runs of non-whitespace that contain at least one letter or digit, so standalone
 * Markdown markers (`---`, `#`, `-`, `>`) are not words. An inline image, alt text and
 * destination alike, is not prose and counts for nothing. Single pass: a whole manuscript is
 * one string, not a word list.
 */
fun countWords(text: String): Int {
	var count = 0
	var counted = false
	val length = text.length
	var i = 0
	while (i < length) {
		val char = text[i]
		if (char == '!' && (i == 0 || text[i - 1] != '\\')) {
			val imageEnd = inlineImageEnd(i, length) { text[it] }
			if (imageEnd > 0) {
				i = imageEnd
				continue
			}
		}
		if (char.isWhitespace()) {
			counted = false
		} else if (!counted && char.isLetterOrDigit()) {
			counted = true
			count++
		}
		i++
	}
	return count
}
