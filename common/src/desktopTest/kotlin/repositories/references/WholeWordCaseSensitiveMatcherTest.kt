package repositories.references

import com.darkrockstudios.apps.hammer.common.data.references.MatchKind
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
	fun `Alias ending in punctuation matches`() {
		val hits = matcher.findMatches("Smith Jr. arrived.", listOf(entry(1, "Smith Jr.")))
		assertEquals(1, hits.size)
	}

	@Test
	fun `Accented letter after name blocks match`() {
		val hits = matcher.findMatches("Anaïs and Anaé left.", listOf(entry(1, "Ana")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Accented letter before name blocks match`() {
		val hits = matcher.findMatches("éBob and ñBob left.", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Name ending in accented letter matches whole word`() {
		val hits = matcher.findMatches("Zoë smiled. Zoë's hat.", listOf(entry(1, "Zoë")))
		assertEquals(2, hits.size)
	}

	@Test
	fun `Name ending in accented letter does not match longer word`() {
		val hits = matcher.findMatches("Zoëlle smiled.", listOf(entry(1, "Zoë")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Combining mark after name blocks match`() {
		val hits = matcher.findMatches("Jose\u0301 arrived.", listOf(entry(1, "Jose")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Decomposed name matches decomposed text`() {
		val hits = matcher.findMatches("Jose\u0301 arrived.", listOf(entry(1, "Jose\u0301")))
		assertEquals(1, hits.size)
	}

	@Test
	fun `Cyrillic name does not match inside longer word`() {
		val hits = matcher.findMatches("Иванов пришёл.", listOf(entry(1, "Иван")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Cyrillic name matches whole word`() {
		val hits = matcher.findMatches("Иван пришёл, и Иван ушёл.", listOf(entry(1, "Иван")))
		assertEquals(2, hits.size)
	}

	@Test
	fun `Greek name does not match inside longer word`() {
		val hits = matcher.findMatches("Νίκοσα και αΝίκος", listOf(entry(1, "Νίκο")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Adjacent digits block match`() {
		val hits = matcher.findMatches("Bob2 and 2Bob and Bob٣ left.", listOf(entry(1, "Bob")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Unicode punctuation and whitespace neighbors match`() {
		val hits = matcher.findMatches(
			"«Zoë» \u201cZoë\u201d Zoë\u00a0left ¿Zoë?",
			listOf(entry(1, "Zoë")),
		)
		assertEquals(4, hits.size)
	}

	@Test
	fun `Multi-token alias with accented letters matches whole word`() {
		val hits = matcher.findMatches(
			"Dr. Müller arrived. Dr. Müllers left.",
			listOf(entry(1, "Dr. Müller")),
		)
		assertEquals(1, hits.size)
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
	fun `Overlapping forms across entries go to the longest`() {
		val hits = matcher.findMatches(
			"Adam's apple bobbed. Adam left.",
			listOf(entry(1, "Adam"), entry(2, "Adam's apple")),
		)
		assertEquals(listOf(2 to 0..11, 1 to 21..24), hits.map { it.entryId to it.range })
	}

	@Test
	fun `Longest form wins within one entry`() {
		val hits = matcher.findMatches(
			"Robert Tallow and Tallow",
			listOf(entry(1, "Tallow", "Robert Tallow")),
		)
		assertEquals(listOf("Robert Tallow" to 0..12, "Tallow" to 18..23), hits.map { it.matchedText to it.range })
	}

	@Test
	fun `Longer form that fails the word boundary falls back to the shorter`() {
		val hits = matcher.findMatches(
			"Martha Tallowford and Martha left.",
			listOf(entry(1, "Martha"), entry(2, "Martha Tallow")),
		)
		assertEquals(listOf(1 to 0..5, 1 to 22..27), hits.map { it.entryId to it.range })
	}

	@Test
	fun `Hits carry the range of the matched text`() {
		val hits = matcher.findMatches("Hi Bob, bye Bob.", listOf(entry(1, "Bob")))
		assertEquals(listOf(3..5, 12..14), hits.map { it.range })
		assertTrue(hits.all { it.kind == MatchKind.EXACT })
	}

	@Test
	fun `All caps is not matched by default`() {
		val hits = matcher.findMatches("I WILL NOT GO", listOf(entry(1, "Will")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `All caps form matches when asked and reports the declared form`() {
		val hits = matcher.findMatches(
			"ROBERT TALLOW! Robert Tallow.",
			listOf(entry(1, "Robert Tallow")),
			includeAllCaps = true,
		)
		assertEquals(
			listOf(MatchKind.ALL_CAPS to 0..12, MatchKind.EXACT to 15..27),
			hits.map { it.kind to it.range },
		)
		assertTrue(hits.all { it.matchedText == "Robert Tallow" })
	}

	@Test
	fun `Mixed case other than all caps does not match`() {
		val hits = matcher.findMatches("ROBert tallow", listOf(entry(1, "Robert Tallow")), includeAllCaps = true)
		assertEquals(0, hits.size)
	}

	@Test
	fun `Entry declared in caps is exact rather than another entry's all caps`() {
		val hits = matcher.findMatches("BOB arrived.", listOf(entry(1, "Bob"), entry(2, "BOB")), includeAllCaps = true)
		assertEquals(listOf(2 to MatchKind.EXACT), hits.map { it.entryId to it.kind })
	}

	@Test
	fun `Plurals are not matched by default`() {
		val hits = matcher.findMatches("The Tallows left.", listOf(entry(1, "Tallow")))
		assertEquals(0, hits.size)
	}

	@Test
	fun `Plurals match when asked`() {
		val hits = matcher.findMatches(
			"The Tallows and the Marshes left.",
			listOf(entry(1, "Tallow"), entry(2, "Marsh")),
			includePlurals = true,
		)
		assertEquals(
			listOf(Triple(1, MatchKind.PLURAL, 4..10), Triple(2, MatchKind.PLURAL, 20..26)),
			hits.map { Triple(it.entryId, it.kind, it.range) },
		)
	}

	@Test
	fun `Another entry's exact form beats a plural`() {
		val hits = matcher.findMatches(
			"Tallows arrived.",
			listOf(entry(1, "Tallow"), entry(2, "Tallows")),
			includePlurals = true,
		)
		assertEquals(listOf(2 to MatchKind.EXACT), hits.map { it.entryId to it.kind })
	}

	@Test
	fun `Possessive matches only the name`() {
		val hits = matcher.findMatches("Tallow's hat and Tallow’s coat", listOf(entry(1, "Tallow")))
		assertEquals(listOf(0..5, 17..22), hits.map { it.range })
	}

	@Test
	fun `Form shared by two entries hits both at the same range`() {
		val hits = matcher.findMatches("Tallow waved.", listOf(entry(1, "Tallow"), entry(2, "Martha", "Tallow")))
		assertEquals(setOf(1, 2), hits.map { it.entryId }.toSet())
		assertTrue(hits.all { it.range == 0..5 })
	}

	@Test
	fun `Same entry list is matched again with the same results`() {
		val entries = listOf(entry(1, "Bob"))
		matcher.findMatches("Bob", entries)
		val hits = matcher.findMatches("Bob and Bob", entries)
		assertEquals(2, hits.size)
		assertEquals(0, matcher.findMatches("Alice", listOf(entry(2, "Alice"))).single().range.first)
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
