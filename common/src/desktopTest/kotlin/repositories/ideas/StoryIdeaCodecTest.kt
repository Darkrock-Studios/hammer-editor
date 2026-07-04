package repositories.ideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StoryIdeaCodecTest {

	private lateinit var codec: StoryIdeaCodec

	private val fullIdea = StoryIdea(
		id = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c"),
		created = Instant.parse("2026-07-03T14:22:05Z"),
		updated = Instant.parse("2026-07-03T14:31:48Z"),
		title = "The Lighthouse Keeper's Daughter",
		content = "What if the light itself was the inheritance...\n\nSecond paragraph with **bold**.",
		tags = setOf("gothic", "coastal"),
		promoted = Instant.parse("2026-07-04T09:00:00Z"),
		archived = Instant.parse("2026-07-05T10:00:00Z"),
	)

	private val minimalIdea = StoryIdea(
		id = IdeaId("11111111-2222-3333-4444-555555555555"),
		created = Instant.parse("2026-07-03T14:22:05Z"),
		updated = Instant.parse("2026-07-03T14:22:05Z"),
		content = "Just a spark.",
	)

	@BeforeEach
	fun setup() {
		codec = StoryIdeaCodec(createTomlSerializer())
	}

	@Test
	fun `Full idea round-trips exactly`() {
		val decoded = codec.decode(codec.encode(fullIdea))
		assertEquals(fullIdea, decoded)
	}

	@Test
	fun `Minimal idea round-trips exactly`() {
		val decoded = codec.decode(codec.encode(minimalIdea))
		assertEquals(minimalIdea, decoded)
	}

	@Test
	fun `Absent optionals are omitted from the front matter`() {
		val encoded = codec.encode(minimalIdea)
		assertFalse(encoded.contains("title"))
		assertFalse(encoded.contains("promoted"))
		assertFalse(encoded.contains("archived"))
	}

	@Test
	fun `Unknown front matter keys are ignored`() {
		val text = """
			+++
			id = "11111111-2222-3333-4444-555555555555"
			created = "2026-07-03T14:22:05Z"
			updated = "2026-07-03T14:22:05Z"
			some_future_field = "whatever"
			+++

			Body text.
		""".trimIndent()

		val decoded = codec.decode(text)

		assertEquals("Body text.", decoded.content)
	}

	@Test
	fun `Windows line endings decode`() {
		val text = codec.encode(minimalIdea).replace("\n", "\r\n")

		val decoded = codec.decode(text)

		assertEquals(minimalIdea.id, decoded.id)
		assertEquals("Just a spark.", decoded.content)
	}

	@Test
	fun `Fence line inside content survives round-trip`() {
		val idea = minimalIdea.copy(content = "before\n+++\nafter")

		val decoded = codec.decode(codec.encode(idea))

		assertEquals("before\n+++\nafter", decoded.content)
	}

	@Test
	fun `Trailing newline in content survives round-trip`() {
		val idea = minimalIdea.copy(content = "ends with newline\n")

		val decoded = codec.decode(codec.encode(idea))

		assertEquals("ends with newline\n", decoded.content)
	}

	@Test
	fun `Empty content round-trips`() {
		val idea = minimalIdea.copy(content = "")

		val decoded = codec.decode(codec.encode(idea))

		assertEquals("", decoded.content)
	}

	@Test
	fun `Leading blank lines before opening fence are tolerated`() {
		val text = "\n\n" + codec.encode(minimalIdea)

		val decoded = codec.decode(text)

		assertEquals(minimalIdea.id, decoded.id)
	}

	@Test
	fun `Missing fences returns null from decodeOrNull`() {
		assertNull(codec.decodeOrNull("no front matter here, just prose"))
	}

	@Test
	fun `Missing closing fence returns null from decodeOrNull`() {
		assertNull(codec.decodeOrNull("+++\nid = \"x\"\nno closing fence"))
	}

	@Test
	fun `Malformed front matter returns null and reports the error`() {
		var reported: Exception? = null

		val result = codec.decodeOrNull("+++\nthis is not valid toml @@@\n+++\n\nbody") {
			reported = it
		}

		assertNull(result)
		assertNotNull(reported)
	}

	@Test
	fun `Encoded file is human readable`() {
		val encoded = codec.encode(fullIdea)

		assertTrue(encoded.startsWith("+++\n"))
		assertTrue(encoded.contains("""id = "0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c""""))
		assertTrue(encoded.endsWith("Second paragraph with **bold**."))
	}
}
