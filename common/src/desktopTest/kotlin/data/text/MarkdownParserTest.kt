package data.text

import com.darkrockstudios.apps.hammer.common.data.text.Entity
import com.darkrockstudios.apps.hammer.common.data.text.parseMarkdown
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownParserTest {

	private fun assertEntity(
		entity: Entity,
		type: KClass<out Entity>,
		start: Int,
		end: Int
	) {
		assertTrue(type.isInstance(entity))
		assertEquals(start, entity.start)
		assertEquals(end, entity.end)
	}

	@Test
	fun `Empty string produces empty result`() {
		val result = parseMarkdown("")
		assertEquals("", result.copy)
		assertTrue(result.entities.isEmpty())
	}

	@Test
	fun `Plain text has no entities and is unchanged`() {
		val result = parseMarkdown("hello world")
		assertEquals("hello world", result.copy)
		assertTrue(result.entities.isEmpty())
	}

	@Test
	fun `Bold markers are stripped and span the inner text`() {
		val result = parseMarkdown("**bold**")
		assertEquals("bold", result.copy)
		assertEquals(1, result.entities.size)
		assertEntity(result.entities[0], Entity.Bold::class, 0, 4)
	}

	@Test
	fun `Italic markers are stripped and span the inner text`() {
		val result = parseMarkdown("_italic_")
		assertEquals("italic", result.copy)
		assertEquals(1, result.entities.size)
		assertEntity(result.entities[0], Entity.Italic::class, 0, 6)
	}

	@Test
	fun `Strike through markers are stripped and span the inner text`() {
		val result = parseMarkdown("~~struck~~")
		assertEquals("struck", result.copy)
		assertEquals(1, result.entities.size)
		assertEntity(result.entities[0], Entity.StrikeThrough::class, 0, 6)
	}

	@Test
	fun `Hyperlink keeps the label as copy and captures the url`() {
		val result = parseMarkdown("[text](http://example.com)")
		assertEquals("text", result.copy)
		assertEquals(1, result.entities.size)
		val link = result.entities[0]
		assertIs<Entity.Hyperlink>(link)
		assertEquals("http://example.com", link.url)
		assertEquals(0, link.start)
		assertEquals(4, link.end)
	}

	@Test
	fun `Entity offsets are relative to the sanitized copy not the source`() {
		val result = parseMarkdown("a **b** c")
		assertEquals("a b c", result.copy)
		assertEquals(1, result.entities.size)
		assertEntity(result.entities[0], Entity.Bold::class, 2, 3)
		// Offsets index into the sanitized copy.
		assertEquals("b", result.copy.substring(2, 3))
	}

	@Test
	fun `Multiple entities in one string are all captured`() {
		val result = parseMarkdown("**bold** and _italic_")
		assertEquals("bold and italic", result.copy)
		assertEquals(2, result.entities.size)
		assertEntity(result.entities[0], Entity.Bold::class, 0, 4)
		assertEntity(result.entities[1], Entity.Italic::class, 9, 15)
	}

	@Test
	fun `Hyperlink mid sentence resolves correct offsets`() {
		val result = parseMarkdown("see [here](url) now")
		assertEquals("see here now", result.copy)
		assertEquals(1, result.entities.size)
		val link = result.entities[0]
		assertIs<Entity.Hyperlink>(link)
		assertEquals("url", link.url)
		assertEquals(4, link.start)
		assertEquals(8, link.end)
		assertEquals("here", result.copy.substring(4, 8))
	}

	@Test
	fun `Hyperlink missing closing paren produces no entity`() {
		val result = parseMarkdown("[text](url")
		// Malformed link: the brackets are consumed but no entity is emitted.
		assertTrue(result.entities.isEmpty())
		assertEquals("text(url", result.copy)
	}

	// NOTE: Documents current (quirky) behavior, not necessarily intended behavior.
	// The opening "__" is consumed as one underline-open plus one italic-open, so an
	// underlined span currently also emits a phantom Italic entity over the same range.
	@Test
	fun `Underline currently also emits a phantom italic entity`() {
		val result = parseMarkdown("__under__")
		assertEquals("under", result.copy)
		assertEquals(2, result.entities.size)
		assertEntity(result.entities[0], Entity.Underline::class, 0, 5)
		assertEntity(result.entities[1], Entity.Italic::class, 0, 5)
	}
}
