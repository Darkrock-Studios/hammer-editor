package com.darkrockstudios.apps.hammer.common.data.search

/** Parsed search query: free text plus any `#tags` pulled out of it. */
data class ParsedQuery(
	val text: String,
	val tags: List<String>,
) {
	fun isUsable(): Boolean {
		val tagOk = tags.any { it.length >= MIN_TAG_LENGTH }
		val textOk = text.length >= MIN_QUERY_LENGTH
		return tagOk || textOk
	}

	companion object {
		const val MIN_QUERY_LENGTH = 2
		const val MIN_TAG_LENGTH = 1
	}
}

/** A `#` starts a tag that runs to the next whitespace; everything else is free text. */
fun parseQuery(query: String): ParsedQuery {
	val tags = mutableListOf<String>()
	val textBuilder = StringBuilder()
	var i = 0
	while (i < query.length) {
		val c = query[i]
		if (c == '#') {
			i++
			val tagStart = i
			while (i < query.length && !query[i].isWhitespace()) i++
			if (i > tagStart) tags.add(query.substring(tagStart, i))
		} else {
			textBuilder.append(c)
			i++
		}
	}
	val text = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
	return ParsedQuery(text = text, tags = tags)
}

/**
 * True when [query] appears in [content], comparing the prose a reader sees rather than the
 * Markdown it is stored as, so `well-known` finds text stored as `well\-known`.
 *
 * The query is taken literally. Markdown syntax typed into a search box is not interpreted, because
 * nobody searches for the storage form of their own text.
 */
fun markdownContains(content: String, query: String): Boolean =
	unescapeMarkdown(content).contains(query, ignoreCase = true)

/** True when every needle is contained (case-insensitively) in at least one tag. */
fun Set<String>.matchesAllTags(needles: List<String>): Boolean {
	if (needles.isEmpty()) return true
	return needles.all { needle ->
		any { it.contains(needle, ignoreCase = true) }
	}
}
