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

		assertEquals("eYbSEcvBVcI4OVRxogNVGg", hash)
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
