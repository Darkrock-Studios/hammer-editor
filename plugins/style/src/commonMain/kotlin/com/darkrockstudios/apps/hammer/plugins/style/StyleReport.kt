package com.darkrockstudios.apps.hammer.plugins.style

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class StyleReportInput(
	val project: String,
	/** Scenes or groups to cover, a group standing for its scenes; left out, the whole story. */
	val sceneIds: List<Int> = emptyList(),
)

@Serializable
data class StyleReport(
	val scenes: List<SceneStyle>,
	/** Every covered scene together. Repeated phrases are those repeated within a scene. */
	val total: StyleFigures,
)

@Serializable
data class SceneStyle(val id: Int, val name: String, val figures: StyleFigures)

@Serializable
data class StyleFigures(
	val words: Int,
	val sentences: Int,
	/** Flesch reading ease: higher is easier, 60 to 70 is plain English. Null without prose. */
	val readingEase: Double?,
	/** Flesch-Kincaid grade level. Null without prose. */
	val gradeLevel: Double?,
	val adverbs: Int,
	val adverbsPerThousandWords: Double,
	val topAdverbs: List<Count>,
	/** The share of words inside quotation marks, from 0 to 1. */
	val dialogueRatio: Double,
	val repeatedWords: List<Count>,
	val repeatedPhrases: List<Count>,
) {
	companion object {
		fun of(counts: SceneCounts, repeatThreshold: Int, listSize: Int): StyleFigures {
			val hasProse = counts.words > 0 && counts.sentences > 0
			val wordsPerSentence = if (hasProse) counts.words.toDouble() / counts.sentences else 0.0
			val syllablesPerWord = if (hasProse) counts.syllables.toDouble() / counts.words else 0.0
			val adverbs = counts.adverbs.values.sum()
			return StyleFigures(
				words = counts.words,
				sentences = counts.sentences,
				readingEase = if (hasProse) round1(206.835 - 1.015 * wordsPerSentence - 84.6 * syllablesPerWord) else null,
				gradeLevel = if (hasProse) round1(0.39 * wordsPerSentence + 11.8 * syllablesPerWord - 15.59) else null,
				adverbs = adverbs,
				adverbsPerThousandWords = if (counts.words > 0) round1(adverbs * 1000.0 / counts.words) else 0.0,
				topAdverbs = counts.adverbs.top(minimum = 1, listSize),
				dialogueRatio = if (counts.words > 0) round2(counts.dialogueWords.toDouble() / counts.words) else 0.0,
				repeatedWords = counts.wordCounts.top(repeatThreshold, listSize),
				repeatedPhrases = counts.phraseCounts.top(minimum = 2, listSize),
			)
		}

		private fun Map<String, Int>.top(minimum: Int, size: Int): List<Count> =
			filterValues { it >= minimum }
				.entries
				.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
				.take(size)
				.map { Count(it.key, it.value) }

		private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
		private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
	}
}

@Serializable
data class Count(val text: String, val count: Int)
