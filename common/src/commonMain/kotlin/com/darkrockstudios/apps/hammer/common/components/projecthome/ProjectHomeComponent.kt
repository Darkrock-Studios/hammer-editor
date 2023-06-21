package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectHomeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val showProjectSync: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectBackupRepository: ProjectBackupRepository by inject()
	private val projectEditorRepository: ProjectEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
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
		_state.getAndUpdate {
			it.copy(
				showExportDialog = true
			)
		}
	}

	override fun endProjectExport() {
		_state.getAndUpdate {
			it.copy(
				showExportDialog = false
			)
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

	override fun startProjectSync() = showProjectSync()

	override fun supportsBackup(): Boolean = projectBackupRepository.supportsBackup()

	override fun createBackup(callback: (ProjectBackupDef?) -> Unit) {
		scope.launch {
			val backup = projectBackupRepository.createBackup(projectDef)

			withContext(mainDispatcher) {
				callback(backup)
			}
		}
	}

	override fun onCreate() {
		super.onCreate()

		loadData()

		listenForSyncEvents()
	}

	private fun listenForSyncEvents() {
		scope.launch {
			projectSynchronizer.syncCompleteEvent.receiveAsFlow().collect { success ->
				if (success) {
					loadData()
				}
			}
		}
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
				_state.getAndUpdate {
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
	override fun shouldConfirmClose() = emptySet<CloseConfirm>()
}

val wordRegex = Regex("""(\s+|(\r\n|\r|\n))""")
fun ProjectEditorRepository.countWordsInScene(sceneItem: SceneItem): Int {
	val markdown = loadSceneMarkdownRaw(sceneItem)
	val count = wordRegex.findAll(markdown.trim()).count() + 1
	return count
}