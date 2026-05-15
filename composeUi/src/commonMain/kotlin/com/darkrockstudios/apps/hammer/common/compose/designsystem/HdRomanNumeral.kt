package com.darkrockstudios.apps.hammer.common.compose.designsystem

private val romanPairs = listOf(
	1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
	100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
	10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

/**
 * Convert [n] to a roman numeral string. Returns "0" for 0 and an unsigned
 * roman for negatives (sign discarded — section markers are positive). Used
 * by [HdSectionHeader] and any `§ I`-style marker.
 */
fun romanNumeral(n: Int): String {
	if (n == 0) return "0"
	var remaining = if (n < 0) -n else n
	val sb = StringBuilder()
	for ((value, symbol) in romanPairs) {
		while (remaining >= value) {
			sb.append(symbol)
			remaining -= value
		}
	}
	return sb.toString()
}
