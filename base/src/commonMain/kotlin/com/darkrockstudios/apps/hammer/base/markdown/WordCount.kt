package com.darkrockstudios.apps.hammer.base.markdown

/**
 * Counts runs of non-whitespace that contain at least one letter or digit, so standalone
 * Markdown markers (`---`, `#`, `-`, `>`) are not words. Single pass: a whole manuscript is
 * one string, not a word list.
 */
fun countWords(text: String): Int {
	var count = 0
	var counted = false
	for (char in text) {
		if (char.isWhitespace()) {
			counted = false
		} else if (!counted && char.isLetterOrDigit()) {
			counted = true
			count++
		}
	}
	return count
}
