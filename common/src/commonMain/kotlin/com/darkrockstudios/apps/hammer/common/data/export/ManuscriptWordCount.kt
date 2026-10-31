package com.darkrockstudios.apps.hammer.common.data.export

/** Below this many words a manuscript states its exact count. */
const val MANUSCRIPT_EXACT_WORD_COUNT_LIMIT = 100

/**
 * [count] as a manuscript states it: exact under [MANUSCRIPT_EXACT_WORD_COUNT_LIMIT], to the nearest
 * 100 below 17,500, to the nearest 500 below 40,000, and to the nearest 1,000 above. The band comes
 * from the raw count, and halves round up.
 */
fun manuscriptWordCount(count: Int): Int = when {
	count < MANUSCRIPT_EXACT_WORD_COUNT_LIMIT -> count
	count < 17_500 -> count.roundHalfUpTo(100)
	count < 40_000 -> count.roundHalfUpTo(500)
	else -> count.roundHalfUpTo(1_000)
}

private fun Int.roundHalfUpTo(step: Int): Int = (this + step / 2) / step * step
