package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Dummy test to generate data for entity tests
 */
class EntityTestDataProducer {
	@Test
	fun produceData() {
		val json = Json {
			prettyPrint = false
			encodeDefaults = true
			coerceInputValues = true
		}

		val entity = createScene(7)
		val hash = EntityHasher.hashEntity(entity)
		val jsonStr = json.encodeToString(entity)

		println(hash)
		println(jsonStr)
	}

	private fun createScene(id: Int) = ApiProjectEntity.SceneEntity(
		id = id,
		name = "Scene $id",
		order = id - 1,
		path = listOf(0),
		type = ApiProjectEntity.Type.SCENE,
		content = "Scene $id content",
		outline = "Scene $id outline",
		notes = "Scene $id notes",
		sceneType = ApiSceneType.Scene
	)

	private fun createNote(id: Int) = ApiProjectEntity.NoteEntity(
		id = id,
		type = ApiProjectEntity.Type.NOTE,
		content = "Note $id content",
		created = Instant.fromEpochMilliseconds(1727415440221)
	)

	private fun createEntity(id: Int, type: String) = ApiProjectEntity.EncyclopediaEntryEntity(
		id = id,
		name = "Entry $id",
		type = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY,
		entryType = type,
		text = "Entry $id content",
		tags = setOf("tag$id"),
		image = null
	)

	private fun createTimeline(id: Int) = ApiProjectEntity.TimelineEventEntity(
		id = id,
		type = ApiProjectEntity.Type.TIMELINE_EVENT,
		order = 0,
		date = "Date $id",
		content = "Entry $id content"
	)
}