package data.search

import com.darkrockstudios.apps.hammer.common.data.search.matchesAllTags
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {

	@Test
	fun `parseQuery extracts tags and free text in any order`() {
		val parsed = parseQuery("dragon #fantasy battle #adventure")
		assertEquals(listOf("fantasy", "adventure"), parsed.tags)
		assertEquals("dragon battle", parsed.text)
	}

	@Test
	fun `parseQuery handles tag-only and stray hash`() {
		val tagOnly = parseQuery("#hero")
		assertEquals(listOf("hero"), tagOnly.tags)
		assertEquals("", tagOnly.text)

		val strayHash = parseQuery("# foo")
		assertTrue(strayHash.tags.isEmpty())
		assertEquals("foo", strayHash.text)
	}

	@Test
	fun `parseQuery handles trailing hash and collapses whitespace`() {
		val parsed = parseQuery("  alice   in\twonderland #")
		assertTrue(parsed.tags.isEmpty())
		assertEquals("alice in wonderland", parsed.text)
	}

	@Test
	fun `isUsable requires a tag or two text chars`() {
		assertFalse(parseQuery("a").isUsable())
		assertTrue(parseQuery("ab").isUsable())
		assertTrue(parseQuery("#a").isUsable())
	}

	@Test
	fun `matchesAllTags requires every needle`() {
		val tags = setOf("Fantasy", "draft")
		assertTrue(tags.matchesAllTags(listOf("fan")))
		assertTrue(tags.matchesAllTags(listOf("fan", "draft")))
		assertFalse(tags.matchesAllTags(listOf("fan", "nano")))
	}

	@Test
	fun `matchesAllTags is case-insensitive substring containment`() {
		assertTrue(setOf("NaNoWriMo").matchesAllTags(listOf("nano")))
		assertFalse(setOf("nano").matchesAllTags(listOf("nanowrimo")))
	}

	@Test
	fun `matchesAllTags with no needles always matches`() {
		assertTrue(emptySet<String>().matchesAllTags(emptyList()))
		assertTrue(setOf("any").matchesAllTags(emptyList()))
	}
}
