package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import kotlin.math.ceil
import kotlin.math.max

const val DEFAULT_WORDS_PER_MINUTE = 225
const val DEFAULT_WORDS_PER_PAGE = 300

fun estimateReadingMinutes(wordCount: Int, wpm: Int = DEFAULT_WORDS_PER_MINUTE): Int {
	if (wordCount <= 0 || wpm <= 0) return 0
	return max(1, ceil(wordCount.toDouble() / wpm).toInt())
}

fun estimatePages(wordCount: Int, wpp: Int = DEFAULT_WORDS_PER_PAGE): Int {
	if (wordCount <= 0 || wpp <= 0) return 0
	return max(1, ceil(wordCount.toDouble() / wpp).toInt())
}
