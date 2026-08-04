package com.darkrockstudios.apps.hammer.common.data.search

import com.darkrockstudios.apps.hammer.common.data.tagindex.isTagPrefix
import com.darkrockstudios.apps.hammer.common.data.tagindex.isTagSeparator
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle

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

/**
 * A `#` opening a word starts a tag; everything else is free text. Tags are split and normalized
 * the way tag input splits them on save, so a needle typed decomposed, or joined to the next by a
 * comma, still matches what is stored. A `#` inside a word (`C#`) is part of the word.
 */
fun parseQuery(query: String): ParsedQuery {
	val tags = LinkedHashSet<String>()
	val textBuilder = StringBuilder()
	var i = 0
	while (i < query.length) {
		val c = query[i]
		if (c.isTagPrefix() && (i == 0 || query[i - 1].isTagSeparator())) {
			i++
			// A comma chains straight into the next tag; whitespace ends tag context entirely.
			while (i < query.length) {
				val tagStart = i
				while (i < query.length && !query[i].isTagSeparator()) i++
				normalizeTagNeedle(query.substring(tagStart, i))
					.takeIf { it.isNotEmpty() }
					?.let(tags::add)
				if (i < query.length && !query[i].isWhitespace()) i++ else break
			}
		} else {
			textBuilder.append(c)
			i++
		}
	}
	val text = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
	return ParsedQuery(text = text, tags = tags.toList())
}

/**
 * True when [query] appears in [content] once the stored backslash escapes are resolved, so
 * `well-known` finds text stored as `well\-known`.
 *
 * Only escapes are resolved. Emphasis, code and link markers are compared as stored, so a phrase
 * spanning them does not match; see #811. The query is taken literally, so the escaped storage form
 * of a phrase does not match either.
 *
 * Global search needs offsets rather than a boolean, so it resolves and matches separately in
 * `SearchProjectUseCase.findMarkdownMatch`. `MarkdownContainsTest` pins the two to the same answer.
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
