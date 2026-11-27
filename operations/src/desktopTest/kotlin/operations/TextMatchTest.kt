package operations

import com.darkrockstudios.apps.hammer.operations.core.CONTEXT_REACH
import com.darkrockstudios.apps.hammer.operations.core.TextMatcher
import com.darkrockstudios.apps.hammer.operations.core.context
import com.darkrockstudios.apps.hammer.operations.core.normalizeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextMatchTest {
	private fun count(term: String, text: String, caseSensitive: Boolean = false, wholeWord: Boolean = true) =
		TextMatcher(term, caseSensitive, wholeWord).count(normalizeText(text))

	/** Each match's context in [text]. */
	private fun contexts(term: String, text: String): List<String> {
		val normalized = normalizeText(text)
		val matcher = TextMatcher(term, caseSensitive = false, wholeWord = true)
		return matcher.matches(normalized).map { context(normalized, it, it + matcher.term.length) }
	}

	@Test
	fun `a whole word is not found inside a longer one`() {
		val text = "Till dawn, still waiting until the tide; till then."
		assertEquals(2, count("till", text))
		assertEquals(3, count("till", text, wholeWord = false))
		assertEquals(0, count("harbor", "The harbormaster's office."))
		assertEquals(1, count("harbor", "The harbormaster's office.", wholeWord = false))
		assertEquals(1, count("harbor", "The harbor's mouth."))
		assertEquals(0, count("tide", "tide2 and 3tide"))
	}

	@Test
	fun `a padded term is still a whole word, and a term's marks are taken as written`() {
		val text = "The harbormaster left the harbor."
		assertEquals(1, count(" harbor ", text))
		assertEquals(0, count("*harbor*", text))
		assertEquals(1, count("*harbor*", "a \\*harbor\\* sign"))
	}

	@Test
	fun `case is ignored unless asked for`() {
		assertEquals(3, count("vell", "Vell, VELL, vell."))
		assertEquals(1, count("Vell", "Vell, VELL, vell.", caseSensitive = true))
		assertEquals(1, count("ÉTÉ", "l'été"))
	}

	@Test
	fun `emphasis marks are dropped from the text where they pair`() {
		assertEquals(3, count("E.V.", "*E.V. E.V. E.V.*"))
		assertEquals(1, count("the Gull", "the **Gull**"))
		assertEquals(1, count("the Gull", "the _Gull_"))
		assertEquals(1, count("Thawmonth", "*Sealed this ninth day of Thawmonth.*"))
		assertEquals(1, count("2 * 3", "2 * 3 = 6"))
		assertEquals(1, count("user_name", "user_name"))
	}

	@Test
	fun `images count for nothing, alt text and names alike`() {
		val text = "The harbor slept. ![The harbor at dawn](images/harbor.png) ![harbor](hammer://image/3)"
		assertEquals(1, count("harbor", text))
		assertEquals(0, count("dawn", text))
		assertEquals("The harbor slept.", normalizeText(text))
	}

	@Test
	fun `the marks around styled text are dropped`() {
		assertEquals(1, count("struck out and marked", "~~struck~~ out and ==marked=="))
		assertEquals(1, count("an underlined word", "an <u>underlined</u> word"))
		assertEquals(1, count("a === b", "a === b"))
		assertEquals(1, count("x ~ y", "x ~ y"))
	}

	@Test
	fun `a backslash escape is the character it escapes`() {
		assertEquals(1, count("5 * 3", "5 \\* 3"))
		assertEquals(1, count("snake_case", "snake\\_case"))
		assertEquals(1, count("1. First", "1\\. First"))
		assertEquals(1, count("C:\\dir", "C:\\dir"))
	}

	@Test
	fun `a context is not cut inside a surrogate pair`() {
		val text = "\uD83D\uDE00".repeat(60) + "tide" + "\uD83D\uDE00".repeat(60)
		val context = contexts("tide", text).single()
		assertTrue(!context[1].isLowSurrogate())
		assertTrue(!context[context.length - 2].isHighSurrogate())
	}

	@Test
	fun `typographic quotes and apostrophes match straight ones`() {
		assertEquals(2, count("Teo's", "Teo’s hand, Teo's boat."))
		assertEquals(2, count("Teo’s", "Teo’s hand, Teo's boat."))
		assertEquals(1, count("\"Here,\"", "“Here,” he said."))
		assertEquals(1, count("'tis", "‘tis so"))
	}

	@Test
	fun `whitespace runs are one space, in the text and the term`() {
		assertEquals(1, count("stone lamps", "two stone \t lamps"))
		assertEquals(1, count("stone   lamps", "two stone lamps"))
		assertEquals(1, count("stone lamps", "two stone\nlamps"))
		assertEquals(1, count("stone lamps", "two *stone* *lamps*"))
		assertEquals(1, count("  lamps ", "two lamps"))
	}

	@Test
	fun `a match does not span a paragraph break`() {
		assertEquals(0, count("stone lamps", "two stone\n\nlamps"))
		assertEquals(0, count("stone lamps", "two stone\r\n\r\nlamps"))
		assertEquals("A\nB C", normalizeText("  A \n \n *B*\nC  "))
	}

	@Test
	fun `matches do not overlap and are found left to right`() {
		val matcher = TextMatcher("aa", caseSensitive = false, wholeWord = false)
		assertEquals(listOf(0, 2), matcher.matches("aaaaa"))
		assertEquals(2, count("bell bell", "bell bell bell bell bell"))
	}

	@Test
	fun `a term edged with punctuation is bounded only on its letter sides`() {
		assertEquals(2, count(",\"", "\"Here,\" he said. \"Not here,\" he said."))
		assertEquals(1, count("Mr.", "Mr. Kade, Mrs. Pike"))
		// "Mr." ends in a stop, so a letter after it doesn't stop the match.
		assertEquals(1, count("Mr.", "Mr.Kade"))
		// A rejected candidate is passed over a character at a time, not by the term's length.
		assertEquals(1, count("a-a", "ba-a-a"))
	}

	@Test
	fun `letters and digits in any script bound a whole word`() {
		assertEquals(0, count("été", "étés"))
		assertEquals(1, count("été", "l'été."))
		assertEquals(0, count("море", "мореход"))
		assertEquals(1, count("море", "синее море."))
		assertEquals(0, count("cafe", "cafe" + Char(0x0301)))
		assertEquals(0, count("7", "٣7"))
		assertEquals(0, count("山", "\uD840\uDC00山"))
		assertEquals(0, count("\uD840\uDC00", "\uD840\uDC00山"))
		assertEquals(1, count("山", "\uD83D\uDE00山"))
	}

	@Test
	fun `a context reaches about 80 characters either side, cut at a word`() {
		val before = (1..30).joinToString(" ") { "w$it" }
		val after = (31..60).joinToString(" ") { "w$it" }
		val context = contexts("harbor", "$before harbor $after").single()
		assertTrue(context.startsWith("…") && context.endsWith("…"), context)
		val left = context.removePrefix("…").substringBefore(" harbor")
		val right = context.removeSuffix("…").substringAfter("harbor ")
		assertTrue(left.length <= CONTEXT_REACH && left.length > CONTEXT_REACH - 6, left)
		assertTrue(right.length <= CONTEXT_REACH && right.length > CONTEXT_REACH - 6, right)
		assertTrue(before.endsWith(left) && before[before.length - left.length - 1] == ' ', left)
		assertTrue(after.startsWith(right) && after[right.length] == ' ', right)
	}

	@Test
	fun `a context stays in its paragraph, with no ellipsis where the paragraph ends`() {
		val text = "The first paragraph ends here.\n\nThe *harbor* was “quiet”.\n\nThe next one."
		assertEquals(listOf("The harbor was \"quiet\"."), contexts("harbor", text))
		assertEquals(listOf("The first paragraph ends here."), contexts("first", text))
	}

	@Test
	fun `with no space to cut at, a context is cut where the reach ends`() {
		val long = "x".repeat(200)
		val context = contexts("-bell-", "$long-bell-$long").single()
		assertEquals("…" + "x".repeat(CONTEXT_REACH) + "-bell-" + "x".repeat(CONTEXT_REACH) + "…", context)
	}

	@Test
	fun `a blank term matches nothing`() {
		assertEquals("", TextMatcher(" \t\n ", caseSensitive = false, wholeWord = true).term)
		assertEquals(0, count("**", "a *b* c"))
	}
}
