package com.darkrockstudios.apps.hammer.common.data.tagindex

private const val ZERO_WIDTH_NON_JOINER = '\u200C'
private const val ZERO_WIDTH_JOINER = '\u200D'
private const val FULLWIDTH_NUMBER_SIGN = '\uFF03'
private const val FULLWIDTH_COMMA = '\uFF0C'
private const val IDEOGRAPHIC_COMMA = '\u3001'

/**
 * Canonically compose [text] (Unicode NFC) so the two spellings of an accented tag - precomposed
 * `è`, and `e` plus a combining grave - are stored and indexed as one string. Tags are compared
 * by exact equality everywhere downstream, so they have to agree on a single form.
 */
internal expect fun normalizeTagForm(text: String): String

/** True when the supplementary-plane [codePoint] is a letter or a digit. */
internal expect fun isSupplementaryLetterOrDigit(codePoint: Int): Boolean

/** Separates one tag from the next in free-form input, in any script's punctuation. */
fun Char.isTagSeparator(): Boolean =
	isWhitespace() ||
		this == ',' ||
		this == FULLWIDTH_COMMA ||
		this == IDEOGRAPHIC_COMMA

private fun Char.isTagChar(): Boolean =
	isLetterOrDigit() ||
		category == CharCategory.NON_SPACING_MARK ||
		category == CharCategory.COMBINING_SPACING_MARK ||
		this == ZERO_WIDTH_NON_JOINER ||
		this == ZERO_WIDTH_JOINER ||
		this == '_' ||
		this == '-'

/**
 * A tag is a run of letters, digits, combining marks and word-joiners, plus `_` and `-`, and
 * must carry at least one letter or digit so it has something to render.
 */
private fun isValidTag(tag: String): Boolean {
	var hasLetterOrDigit = false
	var i = 0
	while (i < tag.length) {
		val char = tag[i]
		val next = if (i + 1 < tag.length) tag[i + 1] else null
		// Astral characters (rare kanji, Adlam, ...) are a surrogate pair, and neither half is a
		// letter on its own, so they have to be tested as a whole code point.
		if (char.isHighSurrogate() && next != null && next.isLowSurrogate()) {
			if (!isSupplementaryLetterOrDigit(codePointOf(char, next))) return false
			hasLetterOrDigit = true
			i += 2
		} else {
			if (!char.isTagChar()) return false
			if (char.isLetterOrDigit()) hasLetterOrDigit = true
			i++
		}
	}
	return hasLetterOrDigit
}

private fun codePointOf(high: Char, low: Char): Int =
	0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)

private fun String.removeTagPrefix(): String =
	if (startsWith('#') || startsWith(FULLWIDTH_NUMBER_SIGN)) substring(1) else this

fun cleanTags(tags: Set<String>): Set<String> =
	tags.asSequence()
		.map { normalizeTagForm(it.trim()).removeTagPrefix() }
		.filter(::isValidTag)
		.toSet()

/**
 * Parse free-form user tag input into a normalized set: split on whitespace + commas,
 * strip a leading `#`, drop pieces that aren't valid tags.
 */
fun parseTagInput(input: String): Set<String> {
	val pieces = mutableListOf<String>()
	val piece = StringBuilder()
	for (char in input) {
		if (char.isTagSeparator()) {
			if (piece.isNotEmpty()) {
				pieces.add(piece.toString())
				piece.clear()
			}
		} else {
			piece.append(char)
		}
	}
	if (piece.isNotEmpty()) pieces.add(piece.toString())

	return cleanTags(pieces.toSet())
}
