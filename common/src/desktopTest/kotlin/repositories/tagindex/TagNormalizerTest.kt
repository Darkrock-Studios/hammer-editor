package repositories.tagindex

import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TagNormalizerTest {

	@Test
	fun `Clean tags keeps accented letters`() {
		val tags = setOf("th\u00e8me", "caf\u00e9", "a\u00f1o", "\u00c6r\u00f8")

		assertEquals(tags, cleanTags(tags))
	}

	@Test
	fun `Clean tags keeps non-latin scripts`() {
		val tags = setOf("\u043f\u0435\u0440\u0441\u043e\u043d\u0430\u0436", "\u4eba\u7269", "\u3061\u304b\u3089")

		assertEquals(tags, cleanTags(tags))
	}

	@Test
	fun `Clean tags keeps supplementary plane letters`() {
		val rareKanji = "\uD842\uDFB7\u7530"
		val adlam = "\uD83A\uDD00\uD83A\uDD22"

		assertEquals(setOf(rareKanji, adlam), cleanTags(setOf(rareKanji, adlam)))
	}

	@Test
	fun `Clean tags keeps word joiners`() {
		val withZwnj = "\u0645\u06cc\u200c\u0631\u0648\u062f"

		assertEquals(setOf(withZwnj), cleanTags(setOf(withZwnj)))
	}

	@Test
	fun `Clean tags keeps ascii word characters`() {
		val tags = setOf("animal", "side-quest", "act_two", "chapter3")

		assertEquals(tags, cleanTags(tags))
	}

	/** Text entered on iOS/macOS can arrive decomposed: base letter plus a combining mark. */
	@Test
	fun `Clean tags composes decomposed accents`() {
		val decomposed = "the\u0300me"

		assertEquals(setOf("th\u00e8me"), cleanTags(setOf(decomposed)))
	}

	@Test
	fun `Clean tags treats both spellings of a tag as one`() {
		val composed = "th\u00e8me"
		val decomposed = "the\u0300me"

		assertEquals(setOf(composed), cleanTags(setOf(composed, decomposed)))
	}

	@Test
	fun `Clean tags strips leading hash and surrounding whitespace`() {
		assertEquals(setOf("th\u00e8me"), cleanTags(setOf("  #th\u00e8me  ")))
	}

	@Test
	fun `Clean tags strips a fullwidth hash`() {
		assertEquals(setOf("\u4eba\u7269"), cleanTags(setOf("\uff03\u4eba\u7269")))
	}

	@Test
	fun `Clean tags drops separators and punctuation`() {
		val tags = setOf("two words", "comma,tag", "quote\"tag", "slash/tag", "")

		assertEquals(emptySet(), cleanTags(tags))
	}

	@Test
	fun `Clean tags drops tags with no letter or digit`() {
		val tags = setOf("\u0301", "---", "___", "\u200c")

		assertEquals(emptySet(), cleanTags(tags))
	}

	@Test
	fun `Parse tag input splits on whitespace and commas`() {
		val input = " #th\u00e8me, guerre  \u00e9t\u00e9,h\u00e9ros "

		assertEquals(setOf("th\u00e8me", "guerre", "\u00e9t\u00e9", "h\u00e9ros"), parseTagInput(input))
	}

	@Test
	fun `Parse tag input splits on ideographic separators`() {
		val input = "\u4eba\u7269\u3000\u5834\u6240\u3001\u9b54\u6cd5"

		assertEquals(setOf("\u4eba\u7269", "\u5834\u6240", "\u9b54\u6cd5"), parseTagInput(input))
	}

	@Test
	fun `Parse tag input splits on non-breaking spaces`() {
		val input = "one\u00a0two"

		assertEquals(setOf("one", "two"), parseTagInput(input))
	}
}