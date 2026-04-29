package com.darkrockstudios.apps.hammer.common.compose.designsystem

/**
 * Format an integer with comma thousands separators: 1234 → "1,234".
 *
 * Uses manual string-building rather than `String.format` so it works
 * uniformly across Kotlin/JVM, Kotlin/Native, and Kotlin/JS.
 */
fun formatThousands(n: Int): String {
	val negative = n < 0
	val s = (if (negative) -n else n).toString()
	if (s.length <= 3) return if (negative) "-$s" else s
	val sb = StringBuilder()
	val start = s.length % 3
	if (start > 0) {
		sb.append(s, 0, start)
		if (s.length > start) sb.append(',')
	}
	var i = start
	while (i < s.length) {
		sb.append(s, i, i + 3)
		if (i + 3 < s.length) sb.append(',')
		i += 3
	}
	return if (negative) "-$sb" else sb.toString()
}
