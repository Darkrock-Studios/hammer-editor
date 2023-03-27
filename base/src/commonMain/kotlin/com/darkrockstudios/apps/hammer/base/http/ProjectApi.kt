package com.darkrockstudios.apps.hammer.base.http

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HasProjectResponse(
	val lastSync: Instant,
	val lastId: Int
)

@Serializable
data class SaveSceneResponse(
    val saved: Boolean
)

@Serializable
data class LoadSceneResponse(
	val id: Int,
	val sceneType: ApiSceneType,
	val order: Int,
	val name: String,
	val path: List<Int>,
	val content: String
)

enum class ApiSceneType {
	Scene, Group;

	companion object {
		fun fromString(string: String): ApiSceneType? {
			return when (string) {
				"Scene" -> Scene
				"Group" -> Group
				else -> null
			}
		}
	}
}