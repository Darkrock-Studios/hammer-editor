package com.darkrockstudios.apps.hammer.common.data.projectstatistics

/** Counts runs of non-whitespace. Single pass: a whole manuscript is one string, not a word list. */
fun countWords(text: String): Int {
	var count = 0
	var inWord = false
	for (char in text) {
		if (char.isWhitespace()) {
			inWord = false
		} else if (!inWord) {
			inWord = true
			count++
		}
	}
	return count
}
