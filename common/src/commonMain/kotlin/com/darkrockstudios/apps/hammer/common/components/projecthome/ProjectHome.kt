package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectHome : Router, HammerComponent {
	val state: Value<State>

	suspend fun exportProject(path: String)
	fun beginProjectExport()
	fun endProjectExport()
	fun syncProject()
	fun resolveConflict(resolvedEntity: ApiProjectEntity)
	fun endSync()

	data class State(
		val projectDef: ProjectDef,
		val created: String,
		val numberOfScenes: Int = 0,
		val totalWords: Int = 0,
		val wordsByChapter: Map<String, Int> = emptyMap(),
		val encyclopediaEntriesByType: Map<EntryType, Int> = emptyMap(),
		val showExportDialog: Boolean = false,
		val hasServer: Boolean = false,
		val isSyncing: Boolean = false,
		val syncProgress: Float = 0f,
		val entityConflict: EntityConflict<*>? = null,
		val syncLog: List<String>? = null,
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
	}
}