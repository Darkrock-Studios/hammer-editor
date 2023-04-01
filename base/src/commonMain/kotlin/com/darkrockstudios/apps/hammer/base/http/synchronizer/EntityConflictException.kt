package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

sealed class EntityConflictException : Exception("Entity conflict") {
	abstract val entity: ApiProjectEntity

	class SceneConflictException(scene: ApiProjectEntity.SceneEntity) : EntityConflictException() {
		override val entity: ApiProjectEntity.SceneEntity = scene
	}

	class NoteConflictException(scene: ApiProjectEntity.NoteEntity) : EntityConflictException() {
		override val entity: ApiProjectEntity.NoteEntity = scene
	}

	class TimelineEventConflictException(scene: ApiProjectEntity.TimelineEventEntity) : EntityConflictException() {
		override val entity: ApiProjectEntity.TimelineEventEntity = scene
	}

	companion object {
		fun fromEntity(entity: ApiProjectEntity): EntityConflictException {
			return when (entity) {
				is ApiProjectEntity.SceneEntity -> SceneConflictException(entity)
				is ApiProjectEntity.NoteEntity -> NoteConflictException(entity)
				is ApiProjectEntity.TimelineEventEntity -> TimelineEventConflictException(entity)
			}
		}
	}
}