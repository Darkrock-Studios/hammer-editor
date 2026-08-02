package repositories.tagindex

import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TagNormalizerTest {

	@Test
	fun `Clean tags keeps accented letters`() {
		val tags = setOf("thème", "café", "año", "Ærø")

		assertEquals(tags, cleanTags(tags))
	}

	/** Text entered on iOS/macOS can arrive decomposed: base letter plus a combining mark. */
	@Test
	fun `Clean tags keeps decomposed accents`() {
		val decomposed = "the\u0300me"

		assertEquals(setOf(decomposed), cleanTags(setOf(decomposed)))
	}

	@Test
	fun `Clean tags keeps non-latin scripts`() {
		val tags = setOf("персонаж", "人物", "ちから")

		assertEquals(tags, cleanTags(tags))
	}

	@Test
	fun `Clean tags keeps ascii word characters`() {
		val tags = setOf("animal", "side-quest", "act_two", "chapter3")

		assertEquals(tags, cleanTags(tags))
	}

	@Test
	fun `Clean tags strips leading hash and surrounding whitespace`() {
		assertEquals(setOf("thème"), cleanTags(setOf("  #thème  ")))
	}

	@Test
	fun `Clean tags drops separators and punctuation`() {
		val tags = setOf("two words", "comma,tag", "quote\"tag", "slash/tag", "")

		assertEquals(emptySet(), cleanTags(tags))
	}

	@Test
	fun `Parse tag input splits on whitespace and commas`() {
		val input = " #thème, guerre  été,héros "

		assertEquals(setOf("thème", "guerre", "été", "héros"), parseTagInput(input))
	}
}
