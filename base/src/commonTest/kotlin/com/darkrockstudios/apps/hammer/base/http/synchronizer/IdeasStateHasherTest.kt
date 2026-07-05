package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdeasStateHasherTest {

	private fun item(uuid: String, hash: String) = IdeaHashItem(IdeaId(uuid), hash)

	// Golden values: client baselines and server rows are compared through this hash across
	// versions; a drift makes every client re-run the ideas phase (or worse, skip a real change).
	@Test
	fun `golden pin - empty set hash never changes`() {
		assertEquals("z6D33dhMdrxYliMWHPUm8Q", IdeasStateHasher.hash(emptyList()))
	}

	@Test
	fun `golden pin - populated set hash never changes`() {
		val items = listOf(
			item("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c", "hash-a"),
			item("00000000-0000-0000-0000-000000000001", "hash-b"),
		)
		assertEquals("XCM7l6_GBIVj3Qt0eRGk6g", IdeasStateHasher.hash(items))
	}

	@Test
	fun `order does not affect hash`() {
		val a = item("00000000-0000-0000-0000-000000000001", "h1")
		val b = item("00000000-0000-0000-0000-000000000002", "h2")
		assertEquals(IdeasStateHasher.hash(listOf(a, b)), IdeasStateHasher.hash(listOf(b, a)))
	}

	@Test
	fun `idea hash change affects state hash`() {
		val before = item("00000000-0000-0000-0000-000000000001", "h1")
		val after = before.copy(hash = "h2")
		assertNotEquals(
			IdeasStateHasher.hash(listOf(before)),
			IdeasStateHasher.hash(listOf(after)),
		)
	}

	@Test
	fun `added idea affects state hash`() {
		val a = item("00000000-0000-0000-0000-000000000001", "h1")
		val b = item("00000000-0000-0000-0000-000000000002", "h2")
		assertNotEquals(IdeasStateHasher.hash(listOf(a)), IdeasStateHasher.hash(listOf(a, b)))
	}

	@Test
	fun `empty set distinct from single idea`() {
		assertNotEquals(
			IdeasStateHasher.hash(emptyList()),
			IdeasStateHasher.hash(listOf(item("00000000-0000-0000-0000-000000000001", "h1"))),
		)
	}
}
