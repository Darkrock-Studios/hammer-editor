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
data class LoadEntityResponse(
	val type: ApiProjectEntity.Type,
	val entity: ApiProjectEntity
) {
	constructor(scene: ApiProjectEntity.SceneEntity) : this(ApiProjectEntity.Type.SCENE, scene)
}

@Serializable
data class ProjectSynchronizationBegan(
	val syncId: String,
	val lastSync: Instant,
	val lastId: Int
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