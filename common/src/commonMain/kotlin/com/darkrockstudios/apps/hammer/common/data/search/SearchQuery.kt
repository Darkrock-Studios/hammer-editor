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
