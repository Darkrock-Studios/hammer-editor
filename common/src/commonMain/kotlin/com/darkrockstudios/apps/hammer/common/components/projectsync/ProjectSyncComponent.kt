package com.darkrockstudios.apps.hammer.common.components.projectsync

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.soywiz.krypto.encoding.Base64
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectSyncComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissSync: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), ProjectSync {

	private val mainDispatcher by injectMainDispatcher()

	private val projectEditorRepository: ProjectEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val sceneDraftRepository: SceneDraftRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	private var syncJob: Job? = null

	private val _state = MutableValue(
		ProjectSync.State()
	)
	override val state: Value<ProjectSync.State> = _state

	private suspend fun updateSyncLog(log: String?) {
		if (log != null) {
			Napier.d(log)
			withContext(mainDispatcher) {
				_state.reduce {
					val existingLog = it.syncLog
					it.copy(
						syncLog = existingLog + log
					)
				}
			}
		}
	}

	private suspend fun updateSync(show: Boolean, progress: Float, log: String? = null) {
		updateSyncLog(log)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					isSyncing = show,
					syncProgress = progress
				)
			}
		}
	}

	override fun syncProject(onComplete: (Boolean) -> Unit) {
		syncJob?.cancel(CancellationException("Starting another sync"))
		syncJob = scope.launch {
			updateSync(true, 0f, "Project Sync Started")
			val success = projectSynchronizer.sync(::onSyncProgress, ::updateSyncLog, ::onConflict, ::onSyncComplete)
			// Auto-close dialog on success
			if (success) {
				endSync()
			}
			onComplete(success)
		}
	}

	override fun resolveConflict(resolvedEntity: ApiProjectEntity) {
		projectSynchronizer.resolveConflict(resolvedEntity)

		_state.reduce {
			it.copy(
				entityConflict = null
			)
		}
	}

	override fun endSync() {
		scope.launch {
			syncJob = null
			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						entityConflict = null,
						isSyncing = false,
						syncProgress = 0f,
						syncLog = emptyList()
					)
				}

				dismissSync()
			}
		}
	}

	override fun cancelSync() {
		scope.launch {
			syncJob?.cancel(CancellationException("User canceled sync"))
			syncJob = null

			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						entityConflict = null,
						syncProgress = 1f,
					)
				}
			}
		}
	}

	private suspend fun onSyncProgress(progress: Float, log: String? = null) {
		Napier.d("Sync progress: $progress")
		updateSync(true, progress, log)
	}

	private suspend fun onConflict(serverEntity: ApiProjectEntity) {
		Napier.d("Sync conflict")

		when (serverEntity) {
			is ApiProjectEntity.SceneEntity -> onSceneConflict(serverEntity)
			is ApiProjectEntity.NoteEntity -> onNoteConflict(serverEntity)
			is ApiProjectEntity.TimelineEventEntity -> onTimelineEventConflict(serverEntity)
			is ApiProjectEntity.EncyclopediaEntryEntity -> onEncyclopediaEntryConflict(serverEntity)
			is ApiProjectEntity.SceneDraftEntity -> onSceneDraftConflict(serverEntity)
		}
	}

	private suspend fun onNoteConflict(serverEntity: ApiProjectEntity.NoteEntity) {
		val local = notesRepository.getNoteFromId(serverEntity.id)?.note
			?: throw IllegalStateException("Failed to get local note")

		val localEntity = ApiProjectEntity.NoteEntity(
			id = local.id,
			created = local.created,
			content = local.content
		)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					entityConflict = ProjectSync.EntityConflict.NoteConflict(
						serverNote = serverEntity,
						clientNote = localEntity
					)
				)
			}
		}
	}

	private suspend fun onTimelineEventConflict(serverEntity: ApiProjectEntity.TimelineEventEntity) {
		val local = timeLineRepository.getTimelineEvent(serverEntity.id)
			?: throw IllegalStateException("Failed to get local note")

		val localEntity = ApiProjectEntity.TimelineEventEntity(
			id = local.id,
			date = local.date,
			content = local.content,
			order = local.order
		)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					entityConflict = ProjectSync.EntityConflict.TimelineEventConflict(
						serverEvent = serverEntity,
						clientEvent = localEntity
					)
				)
			}
		}
	}

	private suspend fun onEncyclopediaEntryConflict(serverEntity: ApiProjectEntity.EncyclopediaEntryEntity) {
		val local = encyclopediaRepository.loadEntry(serverEntity.id).entry
		val def = local.toDef(projectDef)
		val image = if (encyclopediaRepository.hasEntryImage(def, "jpg")) {
			val imageBytes = encyclopediaRepository.loadEntryImage(def, "jpg")
			val imageBase64 = Base64.encode(imageBytes, url = true)
			ApiProjectEntity.EncyclopediaEntryEntity.Image(imageBase64, "jpg")
		} else {
			null
		}

		val localEntity = ApiProjectEntity.EncyclopediaEntryEntity(
			id = local.id,
			name = local.name,
			entryType = local.type.name,
			text = local.text,
			tags = local.tags,
			image = image
		)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					entityConflict = ProjectSync.EntityConflict.EncyclopediaEntryConflict(
						serverEntry = serverEntity,
						clientEntry = localEntity
					)
				)
			}
		}
	}

	private suspend fun onSceneDraftConflict(serverEntity: ApiProjectEntity.SceneDraftEntity) {
		val local = sceneDraftRepository.getDraftDef(serverEntity.id)
			?: throw IllegalStateException("Failed to get local note")
		val localContent = sceneDraftRepository.loadDraftRaw(local)
			?: throw IllegalStateException("Failed to load local draft content")

		val localEntity = ApiProjectEntity.SceneDraftEntity(
			id = local.id,
			name = local.draftName,
			sceneId = local.sceneId,
			created = local.draftTimestamp,
			content = localContent
		)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					entityConflict = ProjectSync.EntityConflict.SceneDraftConflict(
						serverEntry = serverEntity,
						clientEntry = localEntity
					)
				)
			}
		}
	}

	private suspend fun onSyncComplete() {
		updateSyncLog("Sync complete!")
		updateSync(true, 1f)
	}

	private suspend fun onSceneConflict(serverEntity: ApiProjectEntity.SceneEntity) {
		val local = projectEditorRepository.getSceneItemFromId(serverEntity.id)
			?: throw IllegalStateException("Failed to get local scene")

		val path = projectEditorRepository.getPathSegments(local)
		val content = projectEditorRepository.loadSceneMarkdownRaw(local)

		val localEntity = ApiProjectEntity.SceneEntity(
			id = local.id,
			sceneType = local.type.toApiType(),
			name = local.name,
			order = local.order,
			content = content,
			path = path
		)

		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					entityConflict = ProjectSync.EntityConflict.SceneConflict(
						serverScene = serverEntity,
						clientScene = localEntity
					)
				)
			}
		}
	}
}