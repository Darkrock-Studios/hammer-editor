package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectHomeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectEditorRepository: ProjectEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	private val _state = MutableValue(
		ProjectHome.State(
			projectDef = projectDef,
			numberOfScenes = 0,
			created = ""
		)
	)
	override val state: Value<ProjectHome.State> = _state

	override fun beginProjectExport() {
		_state.reduce {
			it.copy(
				showExportDialog = true
			)
		}
	}

	override fun endProjectExport() {
		_state.reduce {
			it.copy(
				showExportDialog = false
			)
		}
	}

	private suspend fun updateSyncLog(log: String?) {
		if (log != null) {
			Napier.d(log)
			withContext(mainDispatcher) {
				_state.reduce {
					val existingLog = it.syncLog
					if (existingLog == null) {
						it.copy(
							syncLog = listOf(log)
						)
					} else {
						it.copy(
							syncLog = existingLog + log
						)
					}
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

	override fun syncProject() {
		scope.launch {
			updateSync(true, 0f, "Project Sync Started")
			projectSynchronizer.sync(::onSyncProgress, ::updateSyncLog, ::onConflict, ::onSyncComplete)
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
			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						entityConflict = null,
						isSyncing = false,
						syncProgress = 0f,
						syncLog = null
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
					entityConflict = ProjectHome.EntityConflict.NoteConflict(
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
					entityConflict = ProjectHome.EntityConflict.TimelineEventConflict(
						serverEvent = serverEntity,
						clientEvent = localEntity
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
					entityConflict = ProjectHome.EntityConflict.SceneConflict(
						serverScene = serverEntity,
						clientScene = localEntity
					)
				)
			}
		}
	}

	override suspend fun exportProject(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)
		projectEditorRepository.exportStory(hpath)

		withContext(mainDispatcher) {
			endProjectExport()
		}
	}

	override fun onCreate() {
		super.onCreate()

		loadData()
	}

	private fun loadData() {
		scope.launch(dispatcherDefault) {
			val metadata = projectEditorRepository.getMetadata()
			val created = metadata.info.created.formatLocal("dd MMM `yy")

			var sceneSummary: SceneSummary? = null
			projectEditorRepository.sceneListChannel.take(1).collect { summary ->
				sceneSummary = summary
			}
			val tree = sceneSummary?.sceneTree?.root ?: throw IllegalStateException("Failed to get scene tree")
			val numScenes = tree.totalChildren

			var words = 0
			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = projectEditorRepository.countWordsInScene(node.value)
					words += count
				}
			}

			val wordsByChapter = mutableMapOf<String, Int>()
			tree.children.forEach { node ->
				val chapterName = node.value.name
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						val count = projectEditorRepository.countWordsInScene(child.value)
						wordsInChapter += count
					}
				}

				wordsByChapter[chapterName] = wordsInChapter
			}

			encyclopediaRepository.loadEntries()
			val entriesByType = mutableMapOf<EntryType, Int>()
			encyclopediaRepository.entryListFlow.take(1).collect { entries ->
				EntryType.values().forEach { type ->
					val numEntriesOfType = entries.count { it.type == type }
					entriesByType[type] = numEntriesOfType
				}
			}

			withContext(dispatcherMain) {
				_state.reduce {
					it.copy(
						created = created,
						numberOfScenes = numScenes,
						totalWords = words,
						wordsByChapter = wordsByChapter,
						encyclopediaEntriesByType = entriesByType,
						hasServer = globalSettingsRepository.serverSettings != null
					)
				}
			}
		}
	}

	override fun isAtRoot() = true
}

val wordRegex = Regex("""(\s+|(\r\n|\r|\n))""")
fun ProjectEditorRepository.countWordsInScene(sceneItem: SceneItem): Int {
	val markdown = loadSceneMarkdownRaw(sceneItem)
	val count = wordRegex.findAll(markdown.trim()).count() + 1
	return count
}