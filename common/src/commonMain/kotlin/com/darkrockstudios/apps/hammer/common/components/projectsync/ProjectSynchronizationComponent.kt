package com.darkrockstudios.apps.hammer.common.components.projectsync

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import io.github.aakira.napier.Napier
import korlibs.crypto.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectSynchronizationComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissSync: () -> Unit,
	private val reauthorize: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ProjectSynchronization {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val sceneEditorRepository: SceneEditorService by projectInject()
	private val encyclopediaService: EncyclopediaService by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val sceneDraftRepository: SceneDraftRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()
	private val projectDataConflictBroker: ProjectDataConflictBroker by projectInject()

	private var syncJob: Job? = null
	private var conflictListenerJob: Job? = null

	private val _state = MutableValue(
		ProjectSynchronization.State()
	)
	override val state: Value<ProjectSynchronization.State> = _state

	init {
		conflictListenerJob = scope.launch {
			for (conflict in projectDataConflictBroker.conflicts) {
				withContext(mainDispatcher) {
					_state.getAndUpdate {
						it.copy(
							projectDataConflict = ProjectSynchronization.ProjectDataConflictState(
								local = conflict.local,
								server = conflict.server,
								serverHash = conflict.serverHash,
							),
							conflictTitle = Res.string.sync_conflict_project_data_title,
						)
					}
				}
			}
		}
	}

	private suspend fun updateSyncLog(log: SyncLogMessage?) {
		if (log != null) {
			Napier.log(log.level.toNapierLevel(), "ProjectSync", null, "${log.projectName} - ${log.message}")
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					val existingLog = it.syncLog
					it.copy(
						syncLog = existingLog + log
					)
				}
			}
		}
	}

	private suspend fun updateSync(isSyncing: Boolean, progress: Float, log: SyncLogMessage? = null) {
		updateSyncLog(log)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					isSyncing = isSyncing,
					syncProgress = progress
				)
			}
		}
	}

	override fun syncProject(onComplete: (Boolean) -> Unit) {
		syncJob?.cancel(CancellationException("Starting another sync"))
		syncJob = scope.launch {
			updateSync(true, 0f, syncLogI("Project Sync Started", projectDef))
			val success = projectSynchronizer.sync(
				onProgress = ::onSyncProgress,
				onLog = ::updateSyncLog,
				onConflict = ::onConflict,
				onComplete = ::onSyncComplete,
				onUnauthorized = ::onUnauthorized
			)

			_state.getAndUpdate {
				it.copy(
					failed = !success
				)
			}

			// Auto-close dialog on success
			if (success && globalSettingsStore.globalSettings.autoCloseSyncDialog) {
				endSync()
			} else {
				if (!success) {
					showLog(true)
				}
			}

			onComplete(success)
		}
	}

	override fun resolveConflict(resolvedEntity: ApiProjectEntity): ProjectSynchronization.EntityMergeError? {
		val error = when (resolvedEntity) {
			is ApiProjectEntity.EncyclopediaEntryEntity -> {
				null
			}

			is ApiProjectEntity.NoteEntity -> {
				validateNoteEntity(resolvedEntity)
			}

			is ApiProjectEntity.SceneDraftEntity -> {
				validateSceneDraft(resolvedEntity)
			}

			is ApiProjectEntity.SceneEntity -> {
				validateScene(resolvedEntity)
			}

			is ApiProjectEntity.TimelineEventEntity -> {
				validateTimelineEventEntity(resolvedEntity)
			}
		}

		if (error == null) {
			projectSynchronizer.resolveConflict(resolvedEntity)

			_state.getAndUpdate {
				it.copy(
					entityConflict = null,
					conflictTitle = null,
				)
			}
		}

		return error
	}

	override fun resolveProjectDataConflict(resolved: ProjectData) {
		projectDataConflictBroker.resolve(resolved)
		_state.getAndUpdate {
			it.copy(
				projectDataConflict = null,
				conflictTitle = null,
			)
		}
	}

	override fun endSync() {
		scope.launch {
			syncJob = null
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						entityConflict = null,
						projectDataConflict = null,
						conflictTitle = null,
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

			updateSyncLog(syncLogW("User canceled project sync", projectDef))

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						entityConflict = null,
						projectDataConflict = null,
						conflictTitle = null,
						isSyncing = false,
					)
				}
			}
		}
	}

	override fun showLog(show: Boolean) {
		_state.getAndUpdate {
			it.copy(
				showLog = show
			)
		}
	}

	override fun onUnauthorized() {
		_state.getAndUpdate {
			it.copy(
				isSyncing = false,
				failed = true,
				showLog = true
			)
		}

		scope.launch {
			updateSyncLog(syncLogW("Unauthorized: Please log in again", projectDef))
			withContext(mainDispatcher) {
				reauthorize()
			}
		}
	}

	override fun resolveEntryRef(id: Int) = encyclopediaService.findEntryDef(id)

	private suspend fun onSyncProgress(progress: Float, log: SyncLogMessage? = null) {
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
		val local = notesRepository.getNoteById(serverEntity.id)?.note
			?: error("Failed to get local note")

		val localEntity = ApiProjectEntity.NoteEntity(
			id = local.id,
			created = local.created,
			content = local.content,
			tags = local.tags,
		)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					entityConflict = ProjectSynchronization.EntityConflict.NoteConflict(
						serverNote = serverEntity,
						clientNote = localEntity
					),
					conflictTitle = Res.string.sync_conflict_note_title
				)
			}
		}
	}

	private suspend fun onTimelineEventConflict(serverEntity: ApiProjectEntity.TimelineEventEntity) {
		val local = timeLineRepository.getTimelineEvent(serverEntity.id)
			?: error("Failed to get local note")

		val localEntity = ApiProjectEntity.TimelineEventEntity(
			id = local.id,
			date = local.date,
			content = local.content,
			order = local.order,
			tags = local.tags,
		)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					entityConflict = ProjectSynchronization.EntityConflict.TimelineEventConflict(
						serverEvent = serverEntity,
						clientEvent = localEntity
					),
					conflictTitle = Res.string.sync_conflict_timeline_title
				)
			}
		}
	}

	private suspend fun onEncyclopediaEntryConflict(serverEntity: ApiProjectEntity.EncyclopediaEntryEntity) {
		val local = encyclopediaService.loadEntry(serverEntity.id).entry
		val def = local.toDef(projectDef)
		val imageExtension = encyclopediaService.findEntryImageExtension(def)
		val image = if (imageExtension != null) {
			val imageBytes = encyclopediaService.loadEntryImage(def, imageExtension)
			val imageBase64 = Base64.encode(imageBytes, url = true)
			ApiProjectEntity.EncyclopediaEntryEntity.Image(imageBase64, imageExtension)
		} else {
			null
		}

		val localEntity = ApiProjectEntity.EncyclopediaEntryEntity(
			id = local.id,
			name = local.name,
			entryType = local.type.text,
			text = local.text,
			tags = local.tags,
			image = image,
			aliases = local.aliases,
			excludeFromDictionary = local.excludeFromDictionary,
		)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					entityConflict = ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict(
						serverEntry = serverEntity,
						clientEntry = localEntity
					),
					conflictTitle = Res.string.sync_conflict_encyclopedia_title
				)
			}
		}
	}

	private suspend fun onSceneDraftConflict(serverEntity: ApiProjectEntity.SceneDraftEntity) {
		val local = sceneDraftRepository.getDraftDef(serverEntity.id)
			?: error("Failed to get local note")
		val localContent = sceneDraftRepository.loadDraftContent(local)
			?: error("Failed to load local draft content")

		val localEntity = ApiProjectEntity.SceneDraftEntity(
			id = local.id,
			name = local.draftName,
			sceneId = local.sceneId,
			created = local.draftTimestamp,
			content = localContent
		)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					entityConflict = ProjectSynchronization.EntityConflict.SceneDraftConflict(
						serverEntry = serverEntity,
						clientEntry = localEntity
					),
					conflictTitle = Res.string.sync_conflict_scene_draft_title
				)
			}
		}
	}

	private suspend fun onSyncComplete() {
		updateSyncLog(syncLogI("Sync complete!", projectDef))
		updateSync(false, 1f)
	}

	private suspend fun onSceneConflict(serverEntity: ApiProjectEntity.SceneEntity) {
		val local = sceneEditorRepository.getSceneItemFromIdIncludingArchived(serverEntity.id)
			?: error("Failed to get local scene")

		val metadata = sceneEditorRepository.loadSceneMetadata(serverEntity.id)

		val path = sceneEditorRepository.getPathSegments(local)

		// For archived scenes, we need to resolve path from filesystem
		val content = if (local.archived) {
			val scenePath = sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(local.id)
				?: error("Failed to resolve path for archived scene")
			sceneEditorRepository.loadSceneMarkdownRaw(local, scenePath)
		} else {
			sceneEditorRepository.loadSceneMarkdownRaw(local)
		}

		val localEntity = ApiProjectEntity.SceneEntity(
			id = local.id,
			sceneType = local.type.toApiType(),
			name = local.name,
			order = local.order,
			content = content,
			path = path,
			outline = metadata.outline,
			notes = metadata.notes,
			archived = local.archived,
		)

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					entityConflict = ProjectSynchronization.EntityConflict.SceneConflict(
						serverScene = serverEntity,
						clientScene = localEntity
					),
					conflictTitle = Res.string.sync_conflict_scene_title
				)
			}
		}
	}

	private fun validateNoteEntity(resolvedEntity: ApiProjectEntity.NoteEntity): ProjectSynchronization.EntityMergeError.NoteMergeError? {
		val error = notesRepository.validateNote(resolvedEntity.content, resolvedEntity.tags)
		return when (error) {
			NoteError.NONE -> null
			NoteError.EMPTY -> ProjectSynchronization.EntityMergeError.NoteMergeError(
				noteError = Res.string.notes_create_toast_empty.toMsg()
			)

			NoteError.TOO_LONG -> ProjectSynchronization.EntityMergeError.NoteMergeError(
				noteError = Res.string.notes_create_toast_too_long.toMsg()
			)

			NoteError.TAG_TOO_LONG -> ProjectSynchronization.EntityMergeError.NoteMergeError(
				noteError = Res.string.notes_create_toast_tag_too_long.toMsg()
			)
		}
	}

	private fun validateTimelineEventEntity(
		resolvedEntity: ApiProjectEntity.TimelineEventEntity,
	): ProjectSynchronization.EntityMergeError.TimelineEventMergeError? {
		return when (timeLineRepository.validateTags(resolvedEntity.tags)) {
			TimeLineEventError.NONE -> null
			TimeLineEventError.TAG_TOO_LONG -> ProjectSynchronization.EntityMergeError.TimelineEventMergeError(
				tagError = Res.string.timeline_create_toast_tag_too_long.toMsg()
			)
		}
	}

	private fun validateScene(resolvedEntity: ApiProjectEntity.SceneEntity): ProjectSynchronization.EntityMergeError.SceneMergeError? {
		val result = sceneEditorRepository.validateSceneName(resolvedEntity.name)
		return if (isSuccess(result)) {
			null
		} else {
			ProjectSynchronization.EntityMergeError.SceneMergeError(
				nameError = result.displayMessage
			)
		}
	}

	private fun validateSceneDraft(resolvedEntity: ApiProjectEntity.SceneDraftEntity): ProjectSynchronization.EntityMergeError.SceneDraftMergeError? {
		val result = SceneDraftsDatasource.validDraftName(resolvedEntity.name)
		return if (result) {
			null
		} else {
			ProjectSynchronization.EntityMergeError.SceneDraftMergeError(
				nameError = Res.string.scene_draft_invalid_name.toMsg()
			)
		}
	}
}
