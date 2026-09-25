package com.darkrockstudios.apps.hammer.story

object WordCountUtils {
	fun estimateReadingTimeMinutes(wordCount: Int, wordsPerMinute: Int = 225): Int {
		return ((wordCount + wordsPerMinute - 1) / wordsPerMinute).coerceAtLeast(1)
	}

	fun formatWordCount(wordCount: Int): String {
		return when {
			wordCount >= 1_000_000 -> String.format("%.1fM", wordCount / 1_000_000.0)
			wordCount >= 1_000 -> String.format("%.1fK", wordCount / 1_000.0)
			else -> wordCount.toString()
		}
	}
}
