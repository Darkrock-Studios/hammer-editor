package com.darkrockstudios.apps.hammer.base.http

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineEventEntityWireTest {
	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun `label keeps the date json key`() {
		val entity = ApiProjectEntity.TimelineEventEntity(id = 1, order = 0, label = "Late autumn", content = "")
		val encoded = json.parseToJsonElement(json.encodeToString(ApiProjectEntity.TimelineEventEntity.serializer(), entity))
		assertEquals("Late autumn", encoded.jsonObject["date"]?.jsonPrimitive?.content)
		assertTrue("label" !in encoded.jsonObject)
	}

	@Test
	fun `a pre 2_0 payload decodes with defaults`() {
		val legacy = """{"id":4,"order":2,"date":"1920","content":"battle","tags":["war"]}"""
		val decoded = json.decodeFromString(ApiProjectEntity.TimelineEventEntity.serializer(), legacy)
		assertEquals(
			ApiProjectEntity.TimelineEventEntity(id = 4, order = 2, label = "1920", content = "battle", tags = setOf("war")),
			decoded,
		)
		assertEquals(TimelineKind.EVENT, decoded.kind)
	}

	@Test
	fun `story dates and kinds use their compact json forms`() {
		val entity = ApiProjectEntity.TimelineEventEntity(
			id = 9,
			order = 1,
			label = null,
			content = "",
			kind = TimelineKind.SCENE,
			subject = 31,
			start = StoryDate(linkedMapOf("year" to 97L, "day" to 214L)),
		)
		val encoded = json.encodeToString(ApiProjectEntity.TimelineEventEntity.serializer(), entity)
		val tree = json.parseToJsonElement(encoded).jsonObject
		assertEquals("scene", tree["kind"]?.jsonPrimitive?.content)
		assertEquals(json.parseToJsonElement("""{"year":97,"day":214}"""), tree["start"])
		assertEquals(entity, json.decodeFromString(ApiProjectEntity.TimelineEventEntity.serializer(), encoded))
	}

	@Test
	fun `timeline round trips`() {
		val entity = ApiProjectEntity.TimelineEventEntity(
			id = 71,
			order = 11,
			label = null,
			content = "",
			timeline = "a3f09c1e",
		)
		val encoded = json.encodeToString(ApiProjectEntity.TimelineEventEntity.serializer(), entity)
		assertEquals("a3f09c1e", json.parseToJsonElement(encoded).jsonObject["timeline"]?.jsonPrimitive?.content)
		assertEquals(entity, json.decodeFromString(ApiProjectEntity.TimelineEventEntity.serializer(), encoded))
	}

	@Test
	fun `a main event has no timeline key`() {
		val entity = ApiProjectEntity.TimelineEventEntity(id = 1, order = 0, label = null, content = "")
		val encoded = json.parseToJsonElement(json.encodeToString(ApiProjectEntity.TimelineEventEntity.serializer(), entity))
		assertTrue("timeline" !in encoded.jsonObject)

		val legacy = """{"id":1,"order":0,"date":null,"content":""}"""
		assertNull(json.decodeFromString(ApiProjectEntity.TimelineEventEntity.serializer(), legacy).timeline)
	}

	@Test
	fun `timeline definitions round trip`() {
		val def = TimelineDef(
			id = TimelineDef.MAIN_ID,
			name = "Main",
			mode = TimelineMode.DATED,
			calendar = CalendarDef(preset = CalendarPreset.STORY, epochName = "Landing", coarsestUnit = "hour"),
			laneOrder = listOf("Keep", "Alice"),
		)
		val encoded = json.encodeToString(TimelineDef.serializer(), def)
		assertEquals("dated", json.parseToJsonElement(encoded).jsonObject["mode"]?.jsonPrimitive?.content)
		assertEquals(def, json.decodeFromString(TimelineDef.serializer(), encoded))
	}

	@Test
	fun `random timeline ids are 8 hex chars`() {
		repeat(50) {
			val id = TimelineDef.randomId()
			assertTrue(Regex("[0-9a-f]{8}").matches(id), id)
		}
	}
}
