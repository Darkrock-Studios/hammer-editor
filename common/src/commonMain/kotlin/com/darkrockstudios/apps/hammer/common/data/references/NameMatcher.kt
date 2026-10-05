package com.darkrockstudios.apps.hammer.common.data.references

data class MatchHit(
	val entryId: Int,
	val matchedText: String,
)

data class MatchableEntry(
	val entryId: Int,
	val names: List<String>,
)

interface NameMatcher {
	fun findMatches(text: String, entries: List<MatchableEntry>): List<MatchHit>
}

class WholeWordCaseSensitiveMatcher : NameMatcher {
	override fun findMatches(text: String, entries: List<MatchableEntry>): List<MatchHit> {
		if (text.isEmpty() || entries.isEmpty()) return emptyList()

		val hits = mutableListOf<MatchHit>()
		for (entry in entries) {
			for (name in entry.names) {
				val trimmed = name.trim()
				if (trimmed.isEmpty()) continue
				val regex = Regex(NO_WORD_BEFORE + Regex.escape(trimmed) + NO_WORD_AFTER)
				for (match in regex.findAll(text)) {
					hits.add(MatchHit(entryId = entry.entryId, matchedText = trimmed))
				}
			}
		}
		return hits
	}

	companion object {
		// Reject only adjacent letters/combining marks/digits/underscores in any script;
		// punctuation and whitespace are fine on either side. This is more permissive than `\b`,
		// which would fail when the name itself starts or ends with a non-word character
		// (e.g. an alias "Mr. Smith").
		private const val WORD_CHAR = """[\p{L}\p{M}\p{N}_]"""
		private const val NO_WORD_BEFORE = "(?<!$WORD_CHAR)"
		private const val NO_WORD_AFTER = "(?!$WORD_CHAR)"
	}
}
