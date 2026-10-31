package com.darkrockstudios.apps.hammer.base.validate

/**
 * Maps display names to filesystem-safe basenames and back. OS-forbidden ASCII characters become
 * visually similar Unicode lookalikes, so encoded names are safe on Windows/macOS/Linux/iOS.
 */
object FilenameEncoding {
	private val encodeMap: Map<Char, Char> = mapOf(
		':' to '꞉',  // Modifier Letter Colon
		'?' to '？',  // Fullwidth Question Mark
		'/' to '⁄',  // Fraction Slash
		'\\' to '⧹', // Big Reverse Solidus
		'*' to '∗',  // Asterisk Operator
		'"' to '＂',  // Fullwidth Quotation Mark
		'|' to '｜',  // Fullwidth Vertical Line
		'<' to '‹',  // Single Left-Pointing Angle Quotation
		'>' to '›',  // Single Right-Pointing Angle Quotation
	)
	private val decodeMap: Map<Char, Char> = encodeMap.entries.associate { (k, v) -> v to k }

	/**
	 * Encodes a display name into a filesystem-safe form. Trailing `.` and ` ` are trimmed (Windows
	 * constraint). The result still looks essentially like the original to a human.
	 */
	fun encode(displayName: String): String {
		val mapped = buildString(displayName.length) {
			for (ch in displayName) append(encodeMap[ch] ?: ch)
		}
		return mapped.trimEnd('.', ' ')
	}

	/** Inverse of [encode]. Safe to call on names that were never encoded. */
	fun decode(rawName: String): String {
		return buildString(rawName.length) {
			for (ch in rawName) append(decodeMap[ch] ?: ch)
		}
	}

	/** Whether [name] comes back unchanged from [encode] then [decode]. */
	fun roundTrips(name: String): Boolean = decode(encode(name)) == name
}
