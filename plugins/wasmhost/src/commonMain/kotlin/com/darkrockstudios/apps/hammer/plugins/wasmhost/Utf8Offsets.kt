package com.darkrockstudios.apps.hammer.plugins.wasmhost

/**
 * The UTF-16 offset into [text] of [utf8Offset], a byte offset into its UTF-8 encoding. Null when that
 * is past the end or inside a character.
 */
internal fun utf16Offset(text: String, utf8Offset: Int): Int? {
	if (utf8Offset < 0) return null
	var bytes = 0
	var index = 0
	while (bytes < utf8Offset) {
		if (index >= text.length) return null
		val char = text[index]
		val pair = char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
		bytes += when {
			pair -> 4
			char.code < 0x80 -> 1
			char.code < 0x800 -> 2
			else -> 3
		}
		index += if (pair) 2 else 1
	}
	return if (bytes == utf8Offset) index else null
}
