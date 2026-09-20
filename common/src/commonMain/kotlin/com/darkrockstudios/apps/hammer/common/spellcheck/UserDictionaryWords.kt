package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagForm

const val MAX_DICTIONARY_WORD_LENGTH = 64

/**
 * The single normalization rule for user dictionary words: NFC form, trimmed, one token,
 * bounded length. Case is kept as typed. Returns null for input that must not be stored.
 */
fun normalizeDictionaryWord(raw: String): String? {
	val word = normalizeTagForm(raw.trim())
	if (word.isEmpty()) return null
	if (word.any { it.isWhitespace() }) return null
	if (word.length > MAX_DICTIONARY_WORD_LENGTH) return null
	return word
}

/**
 * Normalizes stored words for the spell checker. Only a blank entry is dropped: a word a peer
 * stored under different rules than [normalizeDictionaryWord] is still handed to the checker,
 * so what Project Settings lists and what the checker accepts stay the same set.
 */
fun normalizeStoredDictionaryWords(words: Iterable<String>): Set<String> =
	words.mapNotNullTo(mutableSetOf()) { word -> normalizeTagForm(word.trim()).takeIf { it.isNotEmpty() } }
