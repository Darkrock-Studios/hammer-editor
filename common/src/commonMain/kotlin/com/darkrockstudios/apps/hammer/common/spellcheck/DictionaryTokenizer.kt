package com.darkrockstudios.apps.hammer.common.spellcheck

private val WHITESPACE = Regex("""\s+""")

/**
 * Splits entry names and aliases into single dictionary candidate words.
 * The spell checker matches trimmed, lowercased single tokens only, so
 * multi-word phrases must be split or they would silently never match.
 * Surrounding punctuation is stripped ("Mr." -> "mr") while inner
 * apostrophes and hyphens are kept ("d'Artagnan", "Jean-Luc").
 * Blank and single-character tokens are dropped.
 */
fun tokenizeDictionaryWords(phrases: Iterable<String>): Set<String> =
	phrases.asSequence()
		.flatMap { phrase -> phrase.splitToSequence(WHITESPACE) }
		.map { token -> token.trim { c -> !c.isLetterOrDigit() } }
		.filter { token -> token.length > 1 }
		.map { token -> token.lowercase() }
		.toSet()
