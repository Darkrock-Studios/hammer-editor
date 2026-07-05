package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class IdeaHasherTest {

	private val baseIdea = StoryIdea(
		id = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c"),
		created = Instant.parse("2026-07-04T12:00:00Z"),
		updated = Instant.parse("2026-07-04T12:30:00Z"),
		content = "What if the light itself was the inheritance...",
	)

	// Golden values: v1 hashes must never drift. Client baselines and server-side row hashes
	// are computed by this algorithm; a drift here makes every synced idea look edited.
	@Test
	fun `golden pin - minimal idea hash never changes`() {
		assertEquals("0z-dKj-iirvU-gFdBeaGEg", IdeaHasher.hash(baseIdea))
	}

	@Test
	fun `golden pin - fully populated idea hash never changes`() {
		val full = baseIdea.copy(
			title = "The Lighthouse Keeper's Daughter",
			tags = setOf("gothic", "coastal"),
			promoted = Instant.parse("2026-07-05T09:00:00Z"),
			archived = Instant.parse("2026-07-06T09:00:00Z"),
		)
		assertEquals("kucVhzT3IOEq6VjAKsmgCg", IdeaHasher.hash(full))
	}

	@Test
	fun `same input produces same hash`() {
		assertEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(baseIdea.copy()))
	}

	@Test
	fun `id affects hash`() {
		val other = baseIdea.copy(id = IdeaId("ffffffff-ffff-ffff-ffff-ffffffffffff"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(other))
	}

	@Test
	fun `created affects hash`() {
		val other = baseIdea.copy(created = Instant.parse("2020-01-01T00:00:00Z"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(other))
	}

	@Test
	fun `updated affects hash`() {
		val other = baseIdea.copy(updated = Instant.parse("2020-01-01T00:00:00Z"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(other))
	}

	@Test
	fun `title affects hash`() {
		val titled = baseIdea.copy(title = "A Title")
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(titled))
	}

	@Test
	fun `null vs empty title distinguishable`() {
		val nullTitle = baseIdea.copy(title = null)
		val emptyTitle = baseIdea.copy(title = "")
		assertNotEquals(
			IdeaHasher.hash(nullTitle),
			IdeaHasher.hash(emptyTitle),
			"presence-byte must distinguish a null title from an empty one",
		)
	}

	@Test
	fun `content affects hash`() {
		val other = baseIdea.copy(content = "Something else entirely")
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(other))
	}

	@Test
	fun `tags affect hash`() {
		val tagged = baseIdea.copy(tags = setOf("gothic"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(tagged))
	}

	@Test
	fun `tag insertion order does not affect hash`() {
		val ab = baseIdea.copy(tags = setOf("alpha", "beta"))
		val ba = baseIdea.copy(tags = setOf("beta", "alpha"))
		assertEquals(
			IdeaHasher.hash(ab),
			IdeaHasher.hash(ba),
			"equal tag sets must hash identically regardless of insertion order",
		)
	}

	@Test
	fun `tag boundaries affect hash`() {
		val joined = baseIdea.copy(tags = setOf("ab"))
		val split = baseIdea.copy(tags = setOf("a", "b"))
		assertNotEquals(
			IdeaHasher.hash(joined),
			IdeaHasher.hash(split),
			"concatenation-equivalent tag sets must not collide",
		)
	}

	@Test
	fun `promoted affects hash`() {
		val promoted = baseIdea.copy(promoted = Instant.parse("2026-07-05T09:00:00Z"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(promoted))
	}

	@Test
	fun `null promoted distinguishable from epoch zero`() {
		val nullPromoted = baseIdea.copy(promoted = null)
		val epochPromoted = baseIdea.copy(promoted = Instant.fromEpochSeconds(0))
		assertNotEquals(IdeaHasher.hash(nullPromoted), IdeaHasher.hash(epochPromoted))
	}

	@Test
	fun `archived affects hash`() {
		val archived = baseIdea.copy(archived = Instant.parse("2026-07-06T09:00:00Z"))
		assertNotEquals(IdeaHasher.hash(baseIdea), IdeaHasher.hash(archived))
	}

	@Test
	fun `promoted and archived are not interchangeable`() {
		val stamp = Instant.parse("2026-07-05T09:00:00Z")
		val promoted = baseIdea.copy(promoted = stamp)
		val archived = baseIdea.copy(archived = stamp)
		assertNotEquals(
			IdeaHasher.hash(promoted),
			IdeaHasher.hash(archived),
			"the same timestamp in different fields must hash differently",
		)
	}
}
