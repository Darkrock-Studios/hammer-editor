package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityHasherTest {
	@Test
	fun hashScene() {
		val hash = EntityHasher.hashScene(
			id = 2,
			order = 0,
			path = listOf(0, 1),
			name = "Test",
			type = ApiSceneType.Scene,
			content = "Content"
		)

		assertEquals("FKO1hY5N5aqEfYKCF2RnMQ", hash)
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
}