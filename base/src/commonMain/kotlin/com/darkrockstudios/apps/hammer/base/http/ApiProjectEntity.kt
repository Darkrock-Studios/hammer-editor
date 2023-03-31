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

	enum class Type {
		SCENE,
		NOTE,
		/*
		TIMELINE_EVENT,
		ENCYCLOPEDIA_ENTRY,
		*/;

		companion object {
			fun fromString(string: String): Type? {
				return when (string.trim().lowercase()) {
					"scene" -> SCENE
					"note" -> NOTE
					/*
					"timeline_event" -> timeline_event,
					"encyclopedia_entry" -> encyclopedia_entry,
					*/ else -> null
				}
			}
		}
	}
}