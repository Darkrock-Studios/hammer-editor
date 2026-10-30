package com.darkrockstudios.apps.hammer.base.markdown

/** The text of [name] from base/src/commonTest/resources. */
expect fun readTestResource(name: String): String

internal fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
	if (codePoint < 0x10000) return append(codePoint.toChar())
	val offset = codePoint - 0x10000
	append((0xD800 + (offset shr 10)).toChar())
	return append((0xDC00 + (offset and 0x3FF)).toChar())
}
