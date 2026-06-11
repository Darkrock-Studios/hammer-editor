package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.EntityHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProjectContentHasherTest {

	private val pd = "project-data-hash"

	@Test
	fun `same input produces same hash`() {
		val entities = setOf(EntityHash(1, "a"), EntityHash(2, "b"))
		assertEquals(
			ProjectContentHasher.hash(entities, pd),
			ProjectContentHasher.hash(entities, pd),
		)
	}

	@Test
	fun `entity order does not affect hash`() {
		val forward = listOf(EntityHash(1, "a"), EntityHash(2, "b"), EntityHash(3, "c"))
		val shuffled = listOf(EntityHash(3, "c"), EntityHash(1, "a"), EntityHash(2, "b"))
		assertEquals(
			ProjectContentHasher.hash(forward, pd),
			ProjectContentHasher.hash(shuffled, pd),
			"hash must be independent of entity enumeration order",
		)
	}

	@Test
	fun `changing an entity hash changes the result`() {
		val a = setOf(EntityHash(1, "a"), EntityHash(2, "b"))
		val b = setOf(EntityHash(1, "a"), EntityHash(2, "b2"))
		assertNotEquals(ProjectContentHasher.hash(a, pd), ProjectContentHasher.hash(b, pd))
	}

	@Test
	fun `adding an entity changes the result`() {
		val a = setOf(EntityHash(1, "a"))
		val b = setOf(EntityHash(1, "a"), EntityHash(2, "b"))
		assertNotEquals(ProjectContentHasher.hash(a, pd), ProjectContentHasher.hash(b, pd))
	}

	@Test
	fun `changing the project data hash changes the result`() {
		val entities = setOf(EntityHash(1, "a"))
		assertNotEquals(
			ProjectContentHasher.hash(entities, "data-1"),
			ProjectContentHasher.hash(entities, "data-2"),
		)
	}

	@Test
	fun `an id moving between entities is detected`() {
		// {id=1: "x", id=2: "y"} must not collide with {id=1: "y", id=2: "x"}
		val a = setOf(EntityHash(1, "x"), EntityHash(2, "y"))
		val b = setOf(EntityHash(1, "y"), EntityHash(2, "x"))
		assertNotEquals(ProjectContentHasher.hash(a, pd), ProjectContentHasher.hash(b, pd))
	}

	@Test
	fun `empty project has a stable hash`() {
		assertEquals(
			ProjectContentHasher.hash(emptySet(), pd),
			ProjectContentHasher.hash(emptySet(), pd),
		)
	}
}
