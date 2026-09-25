package com.darkrockstudios.apps.hammer.plugins.style

import kotlinx.serialization.Serializable

/**
 * Counts one scene's prose. English only: the syllable, adverb, and common-word rules assume it.
 * Totals add up across scenes, so a book's figures come from its scenes' counts, not a re-read.
 */
object StyleAnalyzer {
	/** Bumped whenever the counting changes, so cached counts are recomputed. */
	const val VERSION = 2

	fun count(markdown: String): SceneCounts {
		// Headings are titles, not prose.
		val paragraphs = markdown.lines().filterNot { HEADING.containsMatchIn(it) }.map(::stripMarkdown).filter { it.isNotBlank() }
		var sentences = 0
		var syllables = 0
		var dialogueWords = 0
		val adverbs = mutableMapOf<String, Int>()
		val words = mutableMapOf<String, Int>()
		val phrases = mutableMapOf<String, Int>()
		var totalWords = 0

		for (paragraph in paragraphs) {
			// The mark that opened the quotation, or null outside one.
			var openedBy: String? = null
			for (sentence in splitSentences(paragraph)) {
				val tokens = TOKEN.findAll(sentence).toList()
				val sentenceWords = mutableListOf<String>()
				for (token in tokens) {
					val text = token.value
					if (text in QUOTES) {
						openedBy = when {
							text == "“" || text == "‘" -> text
							text == "”" && openedBy == "“" -> null
							// A lone ’ is also a plural possessive, so it closes only a ‘ quotation.
							text == "’" -> if (openedBy == "‘") null else openedBy
							text == "\"" -> if (openedBy == "\"") null else openedBy ?: text
							else -> openedBy
						}
						continue
					}
					val word = text.lowercase().trim('\'', '’')
					if (word.isEmpty()) continue
					sentenceWords += word
					totalWords++
					syllables += syllables(word)
					if (openedBy != null) dialogueWords++
					if (isAdverb(word)) adverbs.merge(word, 1, Int::plus)
					if (word.length >= MIN_REPEAT_LENGTH && word !in COMMON_WORDS) words.merge(word, 1, Int::plus)
				}
				if (sentenceWords.isNotEmpty()) sentences++
				sentenceWords.windowed(PHRASE_LENGTH).forEach { phrase ->
					if (phrase.any { it !in COMMON_WORDS }) phrases.merge(phrase.joinToString(" "), 1, Int::plus)
				}
			}
		}

		return SceneCounts(
			words = totalWords,
			sentences = sentences,
			syllables = syllables,
			dialogueWords = dialogueWords,
			adverbs = adverbs,
			wordCounts = words,
			phraseCounts = phrases.filterValues { it > 1 },
		)
	}

	/** Leading list and quote markers, emphasis, and links, leaving the prose. */
	private fun stripMarkdown(line: String): String =
		line.replace(LINE_MARKER, "")
			.replace(LINK, "$1")
			.replace(EMPHASIS, "")

	/** Split at sentence ends, rejoining after a title such as "Mr." that only looks like one. */
	private fun splitSentences(paragraph: String): List<String> =
		SENTENCE_END.split(paragraph).fold(mutableListOf()) { sentences, piece ->
			if (sentences.isNotEmpty() && TITLE_END.containsMatchIn(sentences.last())) {
				sentences[sentences.lastIndex] = sentences.last() + " " + piece
			} else {
				sentences += piece
			}
			sentences
		}

	/** Vowel groups, less a silent final e; every word has at least one. */
	internal fun syllables(word: String): Int {
		val letters = word.filter { it.isLetter() }
		if (letters.isEmpty()) return 0
		var count = VOWEL_GROUP.findAll(letters).count()
		if (letters.length > 2 && letters.endsWith("e") && !letters.endsWith("le") && letters[letters.length - 2] !in VOWELS) {
			count--
		}
		return count.coerceAtLeast(1)
	}

	internal fun isAdverb(word: String): Boolean =
		word.length > MIN_ADVERB_LENGTH && word.endsWith("ly") && word !in NOT_ADVERBS

	private const val MIN_REPEAT_LENGTH = 4
	private const val MIN_ADVERB_LENGTH = 4
	private const val PHRASE_LENGTH = 3
	private const val VOWELS = "aeiouy"

	private val QUOTES = setOf("\"", "“", "”", "‘", "’")
	private val TOKEN = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*|["“”‘]|’(?!\p{L})""")
	private val TITLE_END = Regex("""(?i)\b(?:mr|mrs|ms|dr|st|prof|jr|sr|mt|vs|etc)\.$""")
	private val SENTENCE_END = Regex("""(?<=[.!?…])["”’)]*\s+""")
	private val VOWEL_GROUP = Regex("[aeiouy]+")
	private val HEADING = Regex("""^\s*#{1,6}\s""")
	private val LINE_MARKER = Regex("""^\s*(?:[-*+]\s+|>\s*|\d+\.\s+)""")
	private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
	private val EMPHASIS = Regex("""[*_~`]+""")

	/** Words ending in -ly that are not adverbs. */
	private val NOT_ADVERBS = setOf(
		"family", "only", "early", "reply", "apply", "supply", "rely", "ally", "belly", "bully", "jelly",
		"holy", "ugly", "july", "italy", "lily", "rally", "fly", "sly", "assembly", "anomaly", "butterfly",
		"costly", "curly", "daily", "deadly", "elderly", "friendly", "ghostly", "hilly", "holly", "homely",
		"jolly", "likely", "lively", "lonely", "lovely", "manly", "monthly", "oily", "orderly", "silly",
		"smelly", "surly", "timely", "unlikely", "weekly", "wily", "woolly", "worldly", "yearly", "chilly",
		"comely", "cowardly", "unruly", "burly", "dilly", "gully", "melancholy", "monopoly", "multiply",
		"poly", "sully", "tally", "wobbly",
	)

	/** Words too common to count as repetition. */
	internal val COMMON_WORDS = setOf(
		"a", "about", "after", "again", "all", "also", "am", "an", "and", "any", "are", "as", "at", "back",
		"be", "because", "been", "before", "being", "but", "by", "can", "could", "did", "do", "does", "down",
		"even", "every", "for", "from", "get", "got", "had", "has", "have", "he", "her", "here", "hers",
		"him", "his", "how", "i", "if", "in", "into", "is", "it", "its", "just", "know", "like", "me", "more",
		"much", "my", "no", "not", "now", "of", "off", "on", "one", "only", "or", "our", "out", "over",
		"said", "she", "so", "some", "than", "that", "the", "their", "them", "then", "there", "these",
		"they", "this", "those", "through", "to", "too", "up", "very", "was", "we", "were", "what", "when",
		"where", "which", "while", "who", "why", "will", "with", "would", "you", "your", "i'm", "it's",
		"don't", "didn't", "that's", "he's", "she's", "i'd", "i'll", "you're", "they're", "we're", "can't",
		"won't", "wasn't", "there's", "let", "going", "would've", "could've", "though", "still",
		"around", "away", "other", "another", "each", "went", "come", "came", "make", "made", "want",
		"look", "looked", "think", "thought", "well", "yes", "yeah", "okay", "good", "right", "never",
		"always", "something", "nothing", "anything", "someone", "anyone", "time", "once",
	)
}

/** One scene's raw counts; [StyleReport] derives its figures from these. */
@Serializable
data class SceneCounts(
	val words: Int,
	val sentences: Int,
	val syllables: Int,
	val dialogueWords: Int,
	val adverbs: Map<String, Int>,
	/** Uncommon words of four letters or more. */
	val wordCounts: Map<String, Int>,
	/** Three-word phrases used more than once, not made only of common words. */
	val phraseCounts: Map<String, Int>,
) {
	operator fun plus(other: SceneCounts) = SceneCounts(
		words = words + other.words,
		sentences = sentences + other.sentences,
		syllables = syllables + other.syllables,
		dialogueWords = dialogueWords + other.dialogueWords,
		adverbs = adverbs.mergeCounts(other.adverbs),
		wordCounts = wordCounts.mergeCounts(other.wordCounts),
		phraseCounts = phraseCounts.mergeCounts(other.phraseCounts),
	)

	companion object {
		val EMPTY = SceneCounts(0, 0, 0, 0, emptyMap(), emptyMap(), emptyMap())
	}
}

private fun Map<String, Int>.mergeCounts(other: Map<String, Int>): Map<String, Int> =
	(keys + other.keys).associateWith { (this[it] ?: 0) + (other[it] ?: 0) }
