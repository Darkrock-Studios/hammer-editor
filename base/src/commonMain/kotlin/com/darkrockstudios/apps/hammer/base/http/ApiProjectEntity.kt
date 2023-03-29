package com.darkrockstudios.apps.hammer.base.http

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

	enum class Type {
		SCENE,
		/*
		TIMELINE_EVENT,
		NOTE,
		ENCYCLOPEDIA_ENTRY,
		*/;

		companion object {
			fun fromString(string: String): Type? {
				return when (string.trim().uppercase()) {
					"SCENE" -> SCENE
					/*
					"TIMELINE_EVENT" -> TIMELINE_EVENT,
					"NOTE" -> NOTE,
					"ENCYCLOPEDIA_ENTRY" -> ENCYCLOPEDIA_ENTRY,
					*/ else -> null
				}
			}
		}
	}
}