package com.darkrockstudios.apps.hammer.common.components.projectsync

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

interface ProjectSync {
	val state: Value<State>

	fun syncProject(onComplete: (Boolean) -> Unit)
	fun resolveConflict(resolvedEntity: ApiProjectEntity)
	fun endSync()
	fun cancelSync()

	data class State(
		val isSyncing: Boolean = false,
		val syncProgress: Float = 0f,
		val entityConflict: EntityConflict<*>? = null,
		val syncLog: List<String> = emptyList(),
		val syncComplete: Boolean = false
	)

	sealed class EntityConflict<T : ApiProjectEntity>(
		val serverEntity: T,
		val clientEntity: T
	) {
		class SceneConflict(
			serverScene: ApiProjectEntity.SceneEntity,
			clientScene: ApiProjectEntity.SceneEntity
		) : EntityConflict<ApiProjectEntity.SceneEntity>(serverScene, clientScene)

		class NoteConflict(
			serverNote: ApiProjectEntity.NoteEntity,
			clientNote: ApiProjectEntity.NoteEntity
		) : EntityConflict<ApiProjectEntity.NoteEntity>(serverNote, clientNote)

		class TimelineEventConflict(
			serverEvent: ApiProjectEntity.TimelineEventEntity,
			clientEvent: ApiProjectEntity.TimelineEventEntity
		) : EntityConflict<ApiProjectEntity.TimelineEventEntity>(serverEvent, clientEvent)

		class EncyclopediaEntryConflict(
			serverEntry: ApiProjectEntity.EncyclopediaEntryEntity,
			clientEntry: ApiProjectEntity.EncyclopediaEntryEntity
		) : EntityConflict<ApiProjectEntity.EncyclopediaEntryEntity>(serverEntry, clientEntry)

		class SceneDraftConflict(
			serverEntry: ApiProjectEntity.SceneDraftEntity,
			clientEntry: ApiProjectEntity.SceneDraftEntity
		) : EntityConflict<ApiProjectEntity.SceneDraftEntity>(serverEntry, clientEntry)
	}
}