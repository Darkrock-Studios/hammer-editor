package com.darkrockstudios.apps.hammer.frontend.utils

private val romanPairs = listOf(
	1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
	100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
	10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

/** [n] as a roman numeral, matching the app's chapter markers. Non-positive values print as digits. */
fun romanNumeral(n: Int): String {
	if (n <= 0) return n.toString()
	var remaining = n
	return buildString {
		for ((value, symbol) in romanPairs) {
			while (remaining >= value) {
				append(symbol)
				remaining -= value
			}
		}
	}
}
