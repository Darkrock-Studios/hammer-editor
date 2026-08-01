package com.darkrockstudios.apps.hammer.common.data.search

private const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/**
 * Resolves the backslash escapes stored in [markdown] so text reads the way it does on screen:
 * `well\-known` becomes `well-known`. Returns [markdown] itself when there is nothing to resolve.
 *
 * Nothing else is touched. Emphasis, code, link and block markers are left exactly as stored,
 * because telling syntax apart from a literal character requires parsing, and guessing wrong
 * silently rewrites the author's words.
 */
fun unescapeMarkdownText(markdown: String): String {
	if (!containsBackslash(markdown)) return markdown

	val sb = StringBuilder(markdown.length)
	var i = 0
	while (i < markdown.length) {
		val c = markdown[i]
		if (c == '\\' && i + 1 < markdown.length && markdown[i + 1] in ASCII_PUNCTUATION) {
			sb.append(markdown[i + 1])
			i += 2
		} else {
			sb.append(c)
			i++
		}
	}
	return sb.toString()
}

private fun containsBackslash(text: String): Boolean {
	var i = 0
	while (i < text.length) {
		if (text[i] == '\\') return true
		i++
	}
	return false
}
