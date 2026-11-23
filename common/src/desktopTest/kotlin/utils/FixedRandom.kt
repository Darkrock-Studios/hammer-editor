package utils

import kotlin.random.Random

/** Answers bounded int rolls with [values] in turn, repeating the last, and counts the rolls. */
class FixedRandom(private vararg val values: Int) : Random() {
	var rolls = 0
		private set

	override fun nextBits(bitCount: Int): Int = error("Only nextInt(until) is supported")

	override fun nextInt(until: Int): Int = values[minOf(rolls++, values.lastIndex)]

	companion object {
		const val WIN = 0
		const val LOSE = 1
		fun winning() = FixedRandom(WIN)
		fun losing() = FixedRandom(LOSE)
	}
}
