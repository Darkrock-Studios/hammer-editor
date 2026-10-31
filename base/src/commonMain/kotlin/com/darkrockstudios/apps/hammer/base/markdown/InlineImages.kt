package com.darkrockstudios.apps.hammer.base.markdown

/**
 * The index of the `]` closing the alt text of the inline image, `![alt](destination "title")`, whose
 * `![` is at [start], or -1 when none starts there. Brackets inside the alt text nest, a backslash
 * escapes the next character, and an image never spans a line. The text is read through [charAt] so a
 * caller can scan a `String` or a `CharArray` without copying it.
 */
inline fun inlineImageAltEnd(start: Int, length: Int, charAt: (Int) -> Char): Int {
	if (start + 1 >= length || charAt(start) != '!' || charAt(start + 1) != '[') return -1
	var depth = 1
	var i = start + 2
	while (i < length) {
		when (charAt(i)) {
			'\\' -> i++
			'\n', '\r' -> return -1
			'[' -> depth++
			']' -> if (--depth == 0) {
				return if (i + 1 < length && charAt(i + 1) == '(') i else -1
			}
		}
		i++
	}
	return -1
}

/**
 * The index just past the `)` ending the image whose alt text closes at [altEnd], as found by
 * [inlineImageAltEnd], or -1 when the destination and optional title are not well formed.
 */
inline fun inlineImageDestinationEnd(altEnd: Int, length: Int, charAt: (Int) -> Char): Int {
	var i = altEnd + 2
	while (i < length && (charAt(i) == ' ' || charAt(i) == '\t')) i++
	if (i < length && charAt(i) == '<') {
		i++
		while (i < length && charAt(i) != '>') {
			when (charAt(i)) {
				'\\' -> i++
				'\n', '\r', '<' -> return -1
			}
			i++
		}
		if (i >= length) return -1
		i++
	} else {
		var depth = 0
		while (i < length) {
			val c = charAt(i)
			if (c == ' ' || c == '\t') break
			when (c) {
				'\\' -> i++
				'\n', '\r' -> return -1
				'(' -> depth++
				')' -> if (depth == 0) return i + 1 else depth--
			}
			i++
		}
	}
	while (i < length && (charAt(i) == ' ' || charAt(i) == '\t')) i++
	if (i >= length) return -1
	val open = charAt(i)
	if (open == '"' || open == '\'' || open == '(') {
		val close = if (open == '(') ')' else open
		i++
		while (i < length && charAt(i) != close) {
			when (charAt(i)) {
				'\\' -> i++
				'\n', '\r' -> return -1
			}
			i++
		}
		if (i >= length) return -1
		i++
		while (i < length && (charAt(i) == ' ' || charAt(i) == '\t')) i++
	}
	return if (i < length && charAt(i) == ')') i + 1 else -1
}

/** The index just past the inline image whose `![` is at [start], or -1 when none starts there. */
inline fun inlineImageEnd(start: Int, length: Int, charAt: (Int) -> Char): Int {
	val altEnd = inlineImageAltEnd(start, length, charAt)
	return if (altEnd < 0) -1 else inlineImageDestinationEnd(altEnd, length, charAt)
}
