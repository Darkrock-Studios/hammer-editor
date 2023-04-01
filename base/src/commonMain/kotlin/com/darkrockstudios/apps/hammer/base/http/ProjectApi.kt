package com.darkrockstudios.apps.hammer.base.http

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SaveEntityResponse(
	val saved: Boolean
)

@Serializable
data class LoadEntityResponse(
	val type: ApiProjectEntity.Type,
	val entity: ApiProjectEntity
) {
	constructor(scene: ApiProjectEntity.SceneEntity) : this(ApiProjectEntity.Type.SCENE, scene)
	constructor(note: ApiProjectEntity.NoteEntity) : this(ApiProjectEntity.Type.NOTE, note)
	constructor(note: ApiProjectEntity.TimelineEventEntity) : this(ApiProjectEntity.Type.TIMELINE_EVENT, note)
}

@Serializable
data class ProjectSynchronizationBegan(
	val syncId: String,
	val lastSync: Instant,
	val lastId: Int
)

enum class ApiSceneType {
	Scene, Group
}