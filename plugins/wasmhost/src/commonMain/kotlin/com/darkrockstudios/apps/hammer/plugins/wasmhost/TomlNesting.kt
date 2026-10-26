package com.darkrockstudios.apps.hammer.plugins.wasmhost

/** Far deeper than any file Hammer reads, and far shallower than what overflows a thread's stack. */
private const val MAX_TOML_NESTING = 64

/**
 * Throws [IllegalArgumentException] when [text] nests deeper than [MAX_TOML_NESTING], counting each
 * array, inline table, and dotted key or table header part as a level. tomlkt recurses once per level,
 * so a deep file would overflow the stack, an Error, before it could be refused.
 *
 * Strings and comments are skipped as tomlkt reads them. Anything else is counted, never parsed, so a
 * float's point counts too: this only ever overcounts.
 */
internal fun requireShallowToml(text: String) {
	// Levels the last table header opened.
	var header = 0
	// Dotted key parts since the last entry began.
	var dots = 0
	// For each open array or inline table, the dots of the key it is the value of.
	val open = ArrayList<Int>()
	var carried = 0
	var lineStart = true
	var i = 0
	while (i < text.length) {
		val c = text[i]
		when {
			c == '#' -> while (i + 1 < text.length && text[i + 1] != '\n') i++
			c == '"' || c == '\'' -> i = skipString(text, i) - 1
			c == '\n' -> dots = 0
			c == '[' && open.isEmpty() && lineStart -> {
				header = 0
				while (i < text.length && text[i] != ']' && text[i] != '\n') {
					when (text[i]) {
						'"', '\'' -> i = skipString(text, i) - 1
						'[', '.' -> header++
					}
					i++
				}
				dots = 0
			}
			c == '[' || c == '{' -> {
				open += dots
				carried += dots
				dots = 0
			}
			c == ']' || c == '}' -> {
				if (open.isNotEmpty()) carried -= open.removeAt(open.lastIndex)
				dots = 0
			}
			c == ',' -> dots = 0
			c == '.' -> dots++
		}
		require(header + carried + dots + open.size <= MAX_TOML_NESTING) { "Nested more than $MAX_TOML_NESTING levels deep" }
		lineStart = c == '\n' || (lineStart && (c == ' ' || c == '\t'))
		i++
	}
}

/** The index just past the string starting at [start]; basic strings take `\` escapes, literal ones do not. */
private fun skipString(text: String, start: Int): Int {
	val quote = text[start]
	val triple = "$quote$quote$quote"
	if (!text.startsWith(triple, start) && text.startsWith("$quote$quote", start)) return start + 2
	val close = if (text.startsWith(triple, start)) triple else quote.toString()
	var i = start + close.length
	while (i < text.length) {
		when {
			quote == '"' && text[i] == '\\' -> i += 2
			text.startsWith(close, i) -> return i + close.length
			else -> i++
		}
	}
	return text.length
}
