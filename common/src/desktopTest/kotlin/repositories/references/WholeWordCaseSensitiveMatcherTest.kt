package repositories.references

import com.darkrockstudios.apps.hammer.common.data.references.MatchableEntry
import com.darkrockstudios.apps.hammer.common.data.references.WholeWordCaseSensitiveMatcher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WholeWordCaseSensitiveMatcherTest {

	private val matcher = WholeWordCaseSensitiveMatcher()

	private fun entry(id: Int, vararg names: String) = MatchableEntry(id, names.toList())

	@Test
	fun `Basic match`() {
		val hits = matcher.findMatches("Bob walked away.", listOf(entry(1, "Bob")))
		assertEquals(1, hits.size)
		assertEquals(1, hits[0].entryId)
		assertEquals("Bob", hits[0].matchedText)
	}

	@Test
	fun `No match when name absent`() {
		val hits = matcher.findMatches("Alice walked away.", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Whole word - no substring match`() {
		val hits = matcher.findMatches("Bobby walked away.", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Whole word - no substring match even mid-word`() {
		val hits = matcher.findMatches("robobob", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Punctuation neighbors all match`() {
		val hits = matcher.findMatches(
			"Bob, Bob. (Bob) Bob's hat - Bob!",
			listOf(entry(1, "Bob")),
		)
		assertEquals(5, hits.size)
	}

	@Test
	fun `Case sensitive - lowercase does not match capitalized name`() {
		val hits = matcher.findMatches("the bob in the river", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Multi-word name matches as a whole`() {
		val hits = matcher.findMatches("John Smith left.", listOf(entry(1, "John Smith")))
		assertEquals(1, hits.size)
	}

	@Test
	fun `Multi-word name does not match longer word`() {
		val hits = matcher.findMatches("John Smithers left.", listOf(entry(1, "John Smith")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Markdown bold neighbors match`() {
		val hits = matcher.findMatches("He saw **Bob** smile.", listOf(entry(1, "Bob")))
		assertEquals(1, hits.size)
	}

	@Test
	fun `Newline and tab neighbors match`() {
		val hits = matcher.findMatches("Bob\n\tBob\nBob", listOf(entry(1, "Bob")))
		assertEquals(3, hits.size)
	}

	@Test
	fun `Multiple distinct entries each get their own hit`() {
		val hits = matcher.findMatches(
			"Bob and Alice left.",
			listOf(entry(1, "Bob"), entry(2, "Alice")),
		)
		assertEquals(2, hits.size)
		assertTrue(hits.any { it.entryId == 1 && it.matchedText == "Bob" })
		assertTrue(hits.any { it.entryId == 2 && it.matchedText == "Alice" })
	}

	@Test
	fun `Same entry matched multiple times returns multiple raw hits`() {
		val hits = matcher.findMatches("Bob, Bob, Bob", listOf(entry(1, "Bob")))
		assertEquals(3, hits.size)
		assertTrue(hits.all { it.entryId == 1 })
	}

	@Test
	fun `Aliases all attribute to the same entry`() {
		val hits = matcher.findMatches(
			"Bob and Bobby and Robert all left.",
			listOf(entry(1, "Robert", "Bob", "Bobby")),
		)
		assertEquals(3, hits.size)
		assertTrue(hits.all { it.entryId == 1 })
		val matched = hits.map { it.matchedText }.toSet()
		assertEquals(setOf("Robert", "Bob", "Bobby"), matched)
	}

	@Test
	fun `Multi-token alias with internal punctuation matches`() {
		val hits = matcher.findMatches("Mr. Smith arrived.", listOf(entry(1, "Mr. Smith")))
		assertEquals(1, hits.size)
		assertEquals("Mr. Smith", hits[0].matchedText)
	}

	@Test
	fun `Empty alias is skipped without crashing`() {
		val hits = matcher.findMatches("anything", listOf(entry(1, "")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Whitespace-only alias is skipped`() {
		val hits = matcher.findMatches("anything", listOf(entry(1, "   ")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Names with regex-special characters are escaped`() {
		val hits = matcher.findMatches(
			"Dr. (Bob) and X+Y arrived.",
			listOf(entry(1, "Dr. (Bob)"), entry(2, "X+Y")),
		)
		assertEquals(2, hits.size)
		assertTrue(hits.any { it.entryId == 1 })
		assertTrue(hits.any { it.entryId == 2 })
	}

	@Test
	fun `Overlapping aliases across entries both hit`() {
		val hits = matcher.findMatches(
			"Adam's apple bobbed.",
			listOf(entry(1, "Adam"), entry(2, "Adam's apple")),
		)
		assertTrue(hits.any { it.entryId == 1 })
		assertTrue(hits.any { it.entryId == 2 })
	}

	@Test
	fun `Empty text returns no hits`() {
		val hits = matcher.findMatches("", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Empty entry list returns no hits`() {
		val hits = matcher.findMatches("Anything goes here", emptyList())
		assertEquals(0, hits.size)
	}
}
