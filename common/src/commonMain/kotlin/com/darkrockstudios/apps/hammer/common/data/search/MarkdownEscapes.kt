package com.darkrockstudios.apps.hammer.common.data.search

private const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/**
 * Resolves CommonMark backslash escapes (`\*` becomes `*`) to their bare ASCII punctuation, so text
 * reads the way it does on screen rather than the way it is stored. Allocates only when [text]
 * actually holds a backslash.
 *
 * Escapes are resolved everywhere, including inside code spans and fenced blocks where CommonMark
 * keeps them literal. Telling those apart needs a parser, and no marker is added or removed either
 * way, so the text is never rewritten beyond dropping a backslash.
 */
fun unescapeMarkdown(text: CharSequence): String {
	if (!text.contains('\\')) return text.toString()

	val sb = StringBuilder(text.length)
	var i = 0
	while (i < text.length) {
		val c = text[i]
		val next = if (i + 1 < text.length) text[i + 1] else null
		if (c == '\\' && next != null && next in ASCII_PUNCTUATION) {
			sb.append(next)
			i += 2
		} else {
			sb.append(c)
			i++
		}
	}
	return sb.toString()
}
