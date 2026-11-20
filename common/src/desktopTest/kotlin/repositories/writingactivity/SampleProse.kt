package repositories.writingactivity

import kotlin.math.pow
import kotlin.random.Random

/**
 * Deterministic stand-in prose: function words at a natural rate, content words Zipf-like from a
 * fixed vocabulary, sentences and paragraphs. Different seeds give unrelated passages.
 */
class SampleProse(seed: Int) {
	private val random = Random(seed)

	fun words(count: Int): List<String> = List(count) { nextWord() }

	/** [count] words as paragraphs of sentences. */
	fun prose(count: Int): String = paragraphs(words(count))

	fun nextWord(): String = if (random.nextDouble() < FUNCTION_WORD_RATE) {
		FUNCTION_WORDS[(random.nextDouble().let { it * it } * FUNCTION_WORDS.size).toInt()]
	} else {
		CONTENT_WORDS[(random.nextDouble().pow(1.5) * CONTENT_WORDS.size).toInt()]
	}

	companion object {
		private const val FUNCTION_WORD_RATE = 0.45
		private const val SENTENCE_WORDS = 14
		private const val PARAGRAPH_SENTENCES = 5

		private val FUNCTION_WORDS = (
			"the a of and to in was he she it that his her with as for had on at by not but from they " +
				"you be this which or have an were one all there their we been would when who so if out up " +
				"into no what could then my more them me him said than its only"
			).split(' ')

		private val SYLLABLES = (
			"ka lo ven tor mi sel dra quen bar fen gil hor jun kel mar nor pel rin sut tal ver wen yor zal"
			).split(' ')

		private val CONTENT_WORDS: List<String> = buildList {
			for (a in SYLLABLES) for (b in SYLLABLES) add(a + b)
			for (a in SYLLABLES) for (b in SYLLABLES.take(6)) add(a + b + a)
		}

		/** Joins [words] into sentences and paragraphs, keeping each word's position. */
		fun paragraphs(words: List<String>): String = buildString {
			words.forEachIndexed { index, word ->
				val sentenceStart = index % SENTENCE_WORDS == 0
				if (index > 0) {
					val paragraphStart = index % (SENTENCE_WORDS * PARAGRAPH_SENTENCES) == 0
					append(
						when {
							paragraphStart -> ".\n\n"
							sentenceStart -> ". "
							else -> " "
						}
					)
				}
				append(if (sentenceStart) word.replaceFirstChar { it.uppercase() } else word)
			}
			if (words.isNotEmpty()) append('.')
		}
	}
}
