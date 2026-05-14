package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class EntityHasherTest {
	@Test
	fun hashScene() {
		val hash = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
		)

		val hash2 = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
		)
		assertEquals(hash, hash2, "Hash should be deterministic")

		val hashDifferentContent = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Different Content",
			outline = "outline",
			notes = "notes",
		)
		assertNotEquals(hash, hashDifferentContent, "Hash should change when content changes")
	}

	@Test
	fun hashNote() {
		val instant = Instant.fromEpochMilliseconds(0)
		val hash = EntityHasher.hashNote(
			id = 2,
			created = instant,
			content = "Content"
		)

		assertEquals("NKZ2n0XDoHLagRABzkb8Yg", hash)
	}

	@Test
	fun hashTimelineEvent() {
		val hash = EntityHasher.hashTimelineEvent(
			id = 2,
			order = 1,
			content = "Content",
			date = "The Futuer"
		)

		assertEquals("SAH6B_pamg_T5MCpWZM6vQ", hash)
	}

	@Test
	fun hashEncyclopediaEntry() {
		val hash = EntityHasher.hashEncyclopediaEntry(
			id = 2,
			name = "The Great Debate",
			entryType = "person",
			text = "Some great content",
			tags = setOf("tag1", "tag2"),
			image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
				base64 = "skjdnviouwenvipnsdv",
				fileExtension = "jpg"
			)
		)

		assertEquals("3ovnUSjH8YPOwpe4yUxUww", hash)
	}

	@Test
	fun hashSceneDraft() {
		val instant = Instant.fromEpochMilliseconds(0)
		val hash = EntityHasher.hashSceneDraft(
			id = 2,
			name = "The Great Debate",
			created = instant,
			content = "Some great content",
		)

		assertEquals("lGUFzy0jQtYbLHWa998BfA", hash)
	}

	@Test
	fun hashSceneWithArchived() {
		val hashNotArchived = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
			archived = false,
		)

		val hashArchived = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
			archived = true,
		)

		assertNotEquals(hashNotArchived, hashArchived, "Hash should differ when archived status changes")

		val hashArchived2 = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
			archived = true,
		)
		assertEquals(hashArchived, hashArchived2, "Archived hash should be deterministic")
	}

	@Test
	fun `hashScene differs when confirmedReferences differ`() {
		val base = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = setOf(1, 2),
		)
		val different = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = setOf(1, 3),
		)
		assertNotEquals(
			base, different,
			"hashScene must include confirmedReferences in the digest"
		)
	}

	@Test
	fun `hashScene confirmedReferences is order-independent`() {
		// Defends against a Set-iteration-order regression - the impl must sort before hashing
		val a = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = linkedSetOf(1, 2, 3),
		)
		val b = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = linkedSetOf(3, 1, 2),
		)
		assertEquals(a, b, "confirmedReferences must be sorted before hashing for stability")
	}

	@Test
	fun `hashScene differs when dismissedReferences differ`() {
		val base = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			dismissedReferences = setOf(5),
		)
		val different = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			dismissedReferences = setOf(5, 6),
		)
		assertNotEquals(
			base, different,
			"hashScene must include dismissedReferences in the digest"
		)
	}

	@Test
	fun `hashScene confirmed and dismissed contribute distinctly`() {
		// Defends against a swapped-arg regression where confirmed and dismissed get
		// folded in identically - the same id sets in opposite slots must hash differently.
		val confirmedOne = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = setOf(7),
			dismissedReferences = emptySet(),
		)
		val dismissedOne = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			confirmedReferences = emptySet(),
			dismissedReferences = setOf(7),
		)
		assertNotEquals(
			confirmedOne, dismissedOne,
			"confirmedReferences and dismissedReferences must contribute distinctly to the hash"
		)
	}

	@Test
	fun `hashEncyclopediaEntry differs when aliases differ`() {
		val base = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(), image = null,
			aliases = listOf("Bob"),
		)
		val different = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(), image = null,
			aliases = listOf("Bob", "Bobby"),
		)
		assertNotEquals(
			base, different,
			"hashEncyclopediaEntry must include aliases in the digest"
		)
	}

	@Test
	fun `hashEncyclopediaEntry alias order is significant`() {
		// aliases is a List, not a Set - reordering is a meaningful change that should
		// propagate through sync so two clients with reordered aliases don't silently diverge.
		val a = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(), image = null,
			aliases = listOf("Bob", "Bobby"),
		)
		val b = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(), image = null,
			aliases = listOf("Bobby", "Bob"),
		)
		assertNotEquals(
			a, b,
			"aliases is ordered (List) - reordering must change the hash so reorderings sync"
		)
	}

	@Test
	fun `hashScene differs when tags differ`() {
		val base = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			tags = emptySet(),
		)
		val different = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			tags = setOf("important"),
		)
		assertNotEquals(
			base, different,
			"hashScene must include tags in the digest"
		)
	}

	@Test
	fun `hashScene tags are order-independent`() {
		// Defends against a Set-iteration-order regression - the impl must sort tags before hashing
		val a = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			tags = linkedSetOf("alpha", "beta", "gamma"),
		)
		val b = EntityHasher.hashScene(
			id = 2, order = 0, path = listOf(0, 1), name = "Test",
			type = ApiSceneType.Scene, content = "Content", outline = "outline", notes = "notes",
			tags = linkedSetOf("gamma", "alpha", "beta"),
		)
		assertEquals(a, b, "tags must be sorted before hashing for stability")
	}

	@Test
	fun hashSceneDefaultArchivedMatchesExplicitFalse() {
		// Hash with default archived (should be false)
		val hashDefault = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
		)

		// Hash with explicit archived = false
		val hashExplicitFalse = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content",
			outline = "outline",
			notes = "notes",
			archived = false,
		)

		assertEquals(hashDefault, hashExplicitFalse, "Default archived should match explicit false")
	}
}
