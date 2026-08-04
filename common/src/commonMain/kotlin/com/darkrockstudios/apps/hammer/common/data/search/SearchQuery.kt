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
 * True when [query] appears in the prose [content] displays as: escapes resolved and paired
 * emphasis, code and block markers dropped, so `well-known` finds `well\-known` and `Chapter One`
 * finds `**Chapter** One`.
 *
 * The raw source is searched as a fallback only for a query spelling out emphasis or code markers,
 * so hunting for `**Chapter**` works while the storage form of prose (`well\-known`) stays
 * unsupported: readers type what they see.
 *
 * Global search needs offsets rather than a boolean, so it projects and matches separately in
 * `SearchProjectUseCase.findMarkdownMatch`. `MarkdownContainsTest` pins the two to the same answer.
 */
fun markdownContains(content: String, query: String): Boolean {
	if (projectMarkdownToPlainText(content).contains(query, ignoreCase = true)) return true
	if (containsInlineMarkup(query)) return content.contains(query, ignoreCase = true)
	return false
}

/** True when every needle is contained (case-insensitively) in at least one tag. */
fun Set<String>.matchesAllTags(needles: List<String>): Boolean {
	if (needles.isEmpty()) return true
	return needles.all { needle ->
		any { it.contains(needle, ignoreCase = true) }
	}
}
