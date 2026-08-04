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
 * True when [query] appears, as literal text, in the prose [content] displays as: escapes resolved
 * and paired emphasis, code and block markers dropped. So `well-known` finds `well\-known`, and
 * `Chapter One` finds `**Chapter** One`.
 *
 * The query is never read as Markdown, and the storage form is never searched. `**Chapter**` finds
 * nothing, because those asterisks are not on screen to be found; `well\-known` finds nothing for
 * the same reason, while text stored as `well\\-known` renders a real backslash and is found by
 * typing one. Markers the projection leaves alone, `5*4` and `user_name`, match where they sit,
 * because there they are prose.
 *
 * Global search needs offsets rather than a boolean, so it projects and matches separately in
 * `SearchProjectUseCase.findMarkdownMatch`. `MarkdownContainsTest` pins the two to the same answer.
 */
fun markdownContains(content: String, query: String): Boolean =
	projectMarkdownToPlainText(content).contains(query, ignoreCase = true)

/** True when every needle is contained (case-insensitively) in at least one tag. */
fun Set<String>.matchesAllTags(needles: List<String>): Boolean {
	if (needles.isEmpty()) return true
	return needles.all { needle ->
		any { it.contains(needle, ignoreCase = true) }
	}
}
