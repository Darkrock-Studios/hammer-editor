package com.darkrockstudios.apps.hammer.base.http

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

sealed interface ApiProjectEntity {
	val type: Type
	val id: Int

	@Serializable
	data class SceneEntity(
		override val type: Type = Type.SCENE,
		override val id: Int,
		val sceneType: ApiSceneType,
		val order: Int,
		val name: String,
		val path: List<Int>,
		val content: String
	) : ApiProjectEntity

	@Serializable
	data class NoteEntity(
		override val type: Type = Type.NOTE,
		override val id: Int,
		val content: String,
		val created: Instant
	) : ApiProjectEntity

	@Serializable
	data class TimelineEventEntity(
		override val type: Type = Type.TIMELINE_EVENT,
		override val id: Int,
		val order: Int,
		val date: String?,
		val content: String
	) : ApiProjectEntity

	@Serializable
	data class EncyclopediaEntryEntity(
		override val type: Type = Type.ENCYCLOPEDIA_ENTRY,
		override val id: Int,
		val name: String,
		val entryType: String,
		val text: String,
		val tags: List<String>,
		val image: Image?,
	) : ApiProjectEntity {
		@Serializable
		data class Image(
			val base64: String,
			val fileExtension: String,
		)
	}

	enum class Type {
		SCENE,
		NOTE,
		TIMELINE_EVENT,
		ENCYCLOPEDIA_ENTRY;

		companion object {
			fun fromString(string: String): Type? {
				return when (string.trim().lowercase()) {
					"scene" -> SCENE
					"note" -> NOTE
					"timeline_event" -> TIMELINE_EVENT
					"encyclopedia_entry" -> ENCYCLOPEDIA_ENTRY
					else -> null
				}
			}
		}
	}
}