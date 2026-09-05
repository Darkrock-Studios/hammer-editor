package com.darkrockstudios.apps.hammer.common.spellcheck

const val MAX_DICTIONARY_WORD_LENGTH = 64

/**
 * The single normalization rule for user dictionary words: trimmed, one token, bounded length.
 * Case is kept as typed. Returns null for input that must not be stored.
 */
fun normalizeDictionaryWord(raw: String): String? {
	val word = raw.trim()
	if (word.isEmpty()) return null
	if (word.any { it.isWhitespace() }) return null
	if (word.length > MAX_DICTIONARY_WORD_LENGTH) return null
	return word
}

fun cleanDictionaryWords(words: Iterable<String>): Set<String> =
	words.mapNotNullTo(mutableSetOf(), ::normalizeDictionaryWord)
