package com.darkrockstudios.apps.hammer.base.diff

/**
 * A token from [tokenize]: a run of word chars, a run of whitespace, or a single punctuation char.
 *
 * `plainStart` is the half-open start offset into the plain text the tokenizer was given.
 */
internal data class Token(
	val text: String,
	val plainStart: Int,
) {
	val plainEnd: Int get() = plainStart + text.length
}

/**
 * Splits text into word / whitespace / punctuation tokens, preserving offsets.
 *
 * Diffing at this granularity (rather than characters or lines) keeps edits aligned to natural
 * word boundaries — the same trick that gives `diff-match-patch`'s "semantic cleanup" most of
 * its visual win on prose.
 */
internal fun tokenize(text: String): List<Token> {
	if (text.isEmpty()) return emptyList()

	val tokens = ArrayList<Token>(text.length / 4)
	var i = 0
	while (i < text.length) {
		val start = i
		val c = text[i]
		when {
			c.isWhitespace() -> {
				while (i < text.length && text[i].isWhitespace()) i++
			}

			c.isLetterOrDigit() || c == '_' || c == '\'' -> {
				// `'` keeps contractions like "don't" as one token.
				while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '\'')) i++
			}

			else -> {
				// Punctuation / symbols: one char per token so each piece can change independently.
				i++
			}
		}
		tokens.add(Token(text = text.substring(start, i), plainStart = start))
	}
	return tokens
}
