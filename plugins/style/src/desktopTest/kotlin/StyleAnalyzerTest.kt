import com.darkrockstudios.apps.hammer.plugins.style.StyleAnalyzer
import com.darkrockstudios.apps.hammer.plugins.style.StyleFigures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StyleAnalyzerTest {

	@Test
	fun `syllables follow vowel groups`() {
		assertEquals(1, StyleAnalyzer.syllables("make"))
		assertEquals(2, StyleAnalyzer.syllables("table"))
		assertEquals(1, StyleAnalyzer.syllables("the"))
		assertEquals(3, StyleAnalyzer.syllables("lighthouses"))
	}

	@Test
	fun `adverbs are -ly words that are not otherwise`() {
		assertTrue(StyleAnalyzer.isAdverb("quickly"))
		assertFalse(StyleAnalyzer.isAdverb("family"))
		assertFalse(StyleAnalyzer.isAdverb("fly"))
	}

	@Test
	fun `counts sentences, dialogue, and adverbs, ignoring markdown`() {
		val counts = StyleAnalyzer.count(
			"""
			# The Storm

			The storm came *early* that year. Alice ran quickly!

			“Get inside,” she said. “Now.”
			""".trimIndent()
		)

		assertEquals(14, counts.words)
		assertEquals(4, counts.sentences)
		assertEquals(3, counts.dialogueWords)
		assertEquals(mapOf("quickly" to 1), counts.adverbs)
	}

	@Test
	fun `single-quoted dialogue counts, and possessives do not end a double-quoted one`() {
		val single = StyleAnalyzer.count("‘Run to the kennel,’ said Alice.")
		assertEquals(4, single.dialogueWords)

		val double = StyleAnalyzer.count("“The dogs’ kennel,” said Alice.")
		assertEquals(3, double.dialogueWords)
	}

	@Test
	fun `titles do not end sentences`() {
		assertEquals(1, StyleAnalyzer.count("Mr. Smith met Dr. Jones.").sentences)
	}

	@Test
	fun `repetition counts uncommon words and phrases said more than once`() {
		val counts = StyleAnalyzer.count("The old lighthouse stood. The old lighthouse leaned. The lighthouse fell.")

		assertEquals(3, counts.wordCounts["lighthouse"])
		assertNull(counts.wordCounts["the"])
		assertEquals(mapOf("the old lighthouse" to 2), counts.phraseCounts)
	}

	@Test
	fun `figures come from counts`() {
		val figures = StyleFigures.of(StyleAnalyzer.count("The cat sat. The dog ran."), repeatThreshold = 3, listSize = 10)

		assertEquals(6, figures.words)
		assertEquals(2, figures.sentences)
		assertEquals(119.2, figures.readingEase)
		assertEquals(0.0, figures.dialogueRatio)
		assertNull(StyleFigures.of(StyleAnalyzer.count(""), 3, 10).readingEase)
	}
}
