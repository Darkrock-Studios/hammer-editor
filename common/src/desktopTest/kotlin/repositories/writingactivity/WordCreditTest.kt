package repositories.writingactivity

import com.darkrockstudios.apps.hammer.common.data.writingactivity.ActivityWords
import com.darkrockstudios.apps.hammer.common.data.writingactivity.MAX_DIFFED_WORDS
import com.darkrockstudios.apps.hammer.common.data.writingactivity.countOriginalWords
import org.junit.jupiter.api.Test
import repositories.writingactivity.SampleProse.Companion.paragraphs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class WordCreditTest {

	/** Credit for editing [baseline] into [current], with [alsoSeen] baselines of other scenes. */
	private fun credit(baseline: String, current: String, vararg alsoSeen: String): Int {
		val anchor = ActivityWords.of(baseline)
		val reproduced = (alsoSeen.map(ActivityWords::of) + anchor).flatMap { it.shingles() }.toSet()
		return countOriginalWords(anchor, ActivityWords.of(current), reproduced)
	}

	private fun credit(baseline: List<String>, current: List<String>, vararg alsoSeen: List<String>): Int =
		credit(paragraphs(baseline), paragraphs(current), *alsoSeen.map(::paragraphs).toTypedArray())

	private val sceneA = SampleProse(1).words(2000)
	private val sceneB = SampleProse(2).words(2000)
	private val fresh = SampleProse(3)

	@Test
	fun `drafting fresh words credits each one`() {
		assertEquals(500, credit(sceneB, sceneB + fresh.words(500), sceneA))
	}

	@Test
	fun `replacing a passage credits nearly every new word`() {
		val replaced = sceneA.take(800) + fresh.words(300) + sceneA.drop(1100)

		val credited = credit(sceneA, replaced)

		assertTrue(credited in 270..300, "credited $credited")
	}

	@Test
	fun `trimming one scene and drafting in another credits the draft`() {
		val trimmed = sceneA.take(800) + sceneA.drop(1100)
		val drafted = sceneB + fresh.words(500)

		val credited = credit(sceneA, trimmed, sceneB) + credit(sceneB, drafted, sceneA)

		assertEquals(500, credited)
	}

	@Test
	fun `moving a passage to another scene credits nothing`() {
		val passage = sceneA.subList(800, 1100)
		val trimmed = sceneA.take(800) + sceneA.drop(1100)
		val received = sceneB.take(1000) + passage + sceneB.drop(1000)

		val credited = credit(sceneA, trimmed, sceneB) + credit(sceneB, received, sceneA)

		assertEquals(0, credited)
	}

	@Test
	fun `moving a passage within a scene credits nothing`() {
		val passage = sceneA.subList(800, 1100)
		val rest = sceneA.take(800) + sceneA.drop(1100)
		val moved = rest.take(1400) + passage + rest.drop(1400)

		assertEquals(0, credit(sceneA, moved))
	}

	@Test
	fun `swapping scattered words credits nearly every swap`() {
		val random = Random(4)
		val positions = generateSequence { random.nextInt(sceneA.size) }.distinct().take(200).toSet()
		val swapped = sceneA.mapIndexed { index, word ->
			if (index in positions) generateSequence { fresh.nextWord() }.first { it != word } else word
		}

		val credited = credit(sceneA, swapped)

		assertTrue(credited in 190..205, "credited $credited")
	}

	@Test
	fun `moving a passage and adding inside it credits only the additions`() {
		val passage = sceneA.subList(800, 1100)
		val trimmed = sceneA.take(800) + sceneA.drop(1100)
		val received = sceneB.take(1000) + passage.take(150) + fresh.words(20) + passage.drop(150) +
			sceneB.drop(1000)

		val credited = credit(sceneA, trimmed, sceneB) + credit(sceneB, received, sceneA)

		assertEquals(20, credited)
	}

	@Test
	fun `fixing a typo credits the corrected word`() {
		assertEquals(1, credit("I went home adn", "I went home and"))
	}

	@Test
	fun `capitalising an existing word credits nothing`() {
		assertEquals(0, credit("the wonderful day", "The wonderful day"))
	}

	@Test
	fun `aligning a paragraph credits nothing`() {
		assertEquals(0, credit("A day\nB night", "A day\n<div align=\"center\">\n\nB night\n\n</div>"))
	}

	@Test
	fun `moving a short paragraph credits nothing`() {
		assertEquals(0, credit("Alpha beta gamma.\n\nDelta epsilon.", "Delta epsilon.\n\nAlpha beta gamma."))
	}

	@Test
	fun `appending words counts each one`() {
		assertEquals(3, credit("I went home", "I went home today and rested"))
	}

	@Test
	fun `replacing words counts the new ones`() {
		assertEquals(2, credit("I went home", "We returned home"))
	}

	@Test
	fun `same text records zero`() {
		assertEquals(0, credit("the quick brown fox", "the quick brown fox"))
	}

	@Test
	fun `deleting words records nothing`() {
		assertEquals(0, credit("the cat sat on the mat", "the cat"))
	}

	@Test
	fun `repeating a word credits the repeat`() {
		assertEquals(1, credit("I I I", "I I I I"))
	}

	@Test
	fun `empty old text counts every new word`() {
		assertEquals(5, credit("", "this is brand new content"))
	}

	@Test
	fun `whitespace-only changes record zero`() {
		assertEquals(0, credit("hello world", "hello   world\n"))
	}

	@Test
	fun `typing an inline image credits nothing`() {
		assertEquals(0, credit("Before", "Before ![Black cat](hammer://image/3)"))
	}

	@Test
	fun `replacing an image's alt text credits nothing`() {
		assertEquals(
			0,
			credit(
				"Before ![Black cat](hammer://image/3) after",
				"Before ![A sleeping white cat](hammer://image/3) after",
			),
		)
	}

	@Test
	fun `a changed vowel sign at a word's end is a new word`() {
		assertEquals(1, credit("वह काम करता है", "वह काम करते है"))
	}

	@Test
	fun `punctuation changes record zero`() {
		assertEquals(0, credit("home", "home."))
	}

	@Test
	fun `a hyphenated compound is two words`() {
		assertEquals(2, credit("", "well-known"))
	}

	@Test
	fun `a contraction is one word`() {
		assertEquals(1, credit("", "don't"))
	}

	@Test
	fun `a number with separators is one word`() {
		assertEquals(1, credit("", "3.14"))
	}

	@Test
	fun `CRLF and LF line endings are not a change`() {
		assertEquals(0, credit("first line\nsecond line", "first line\r\nsecond line"))
	}

	@Test
	fun `rewriting a sentence around kept words credits the rewrite`() {
		assertEquals(
			7,
			credit("A big dog and a small cat slept.", "The huge wolf and the tiny lynx slept."),
		)
	}

	@Test
	fun `inserting words around a kept word credits only the insertions`() {
		assertEquals(2, credit("the cat sat", "the big cat quietly sat"))
	}

	@Test
	fun `past the diff limit a reordered scene credits only its new word`() {
		val baseline = SampleProse(5).words(MAX_DIFFED_WORDS + 1)
		val current = baseline.reversed() + "zzz"

		assertEquals(1, credit(baseline, current))
	}

	@Test
	fun `a heavily reworked long scene is counted without a quadratic diff`() {
		val baseline = SampleProse(6).words(10_000)
		val reworked = baseline.chunked(70).reversed().flatten().mapIndexed { index, word ->
			if (index % 37 == 0) "new$index" else word
		}
		val anchor = ActivityWords.of(paragraphs(baseline))
		val current = ActivityWords.of(paragraphs(reworked))
		val reproduced = anchor.shingles().toSet()

		val credited: Int
		val elapsed = measureTime { credited = countOriginalWords(anchor, current, reproduced) }

		assertTrue(elapsed < 1.seconds, "took $elapsed")
		assertEquals(reworked.count { it.startsWith("new") }, credited)
	}

	@Test
	fun `replacing a short scene with a long paste is counted without a quadratic diff`() {
		val baseline = SampleProse(7).words(100)
		val pasted = SampleProse(8).words(30_000)

		val credited: Int
		val elapsed = measureTime { credited = credit(baseline, pasted) }

		assertTrue(elapsed < 1.seconds, "took $elapsed")
		assertTrue(credited in 29_900..30_000, "credited $credited")
	}
}
