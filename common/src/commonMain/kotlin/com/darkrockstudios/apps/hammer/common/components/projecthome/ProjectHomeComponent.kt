package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_success
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.component.inject

class ProjectHomeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val showProjectSync: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome,
	ComponentToaster by ComponentToasterImpl() {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectBackupRepository: ProjectBackupRepository by inject()
	private val sceneEditorRepository: SceneEditorRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	private val contentRouter = ProjectHomeContentRouter(componentContext, projectDef)
	override val contentRouterState: Value<ChildStack<ProjectHomeContentRouter.Config, ProjectHome.ContentDestination>> =
		contentRouter.state

	private val _state = MutableValue(
		ProjectHome.State(
			projectDef = projectDef,
			numberOfScenes = 0,
			created = "",
			isLoadingStats = true
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

	override suspend fun exportProject(path: String): HPath {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)
		val filePath = sceneEditorRepository.exportStory(hpath)

		withContext(mainDispatcher) {
			endProjectExport()
		}

		return filePath
	}

	override fun startProjectSync() = showProjectSync()

	override fun supportsBackup(): Boolean = projectBackupRepository.supportsBackup()

	override fun createBackup(callback: (ProjectBackupDef?) -> Unit) {
		scope.launch {
			val backup = projectBackupRepository.createBackup(projectDef)

			withContext(mainDispatcher) {
				callback(backup)

				val msg = if (backup != null) {
					Msg(
						Res.string.project_home_action_backup_toast_success,
						backup.path.name
					)
				} else {
					Msg(Res.string.project_home_action_backup_toast_failure)
				}
				showToast(scope, msg)
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
			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(isLoadingStats = true)
				}
			}

			val metadata = sceneEditorRepository.getMetadata()
			val created = metadata.info.created.formatLocal("dd MMM `yy")

			var sceneSummary: SceneSummary? = null
			sceneEditorRepository.sceneListChannel.take(1).collect { summary ->
				sceneSummary = summary
			}
			val tree = sceneSummary?.sceneTree?.root ?: throw IllegalStateException("Failed to get scene tree")
			var numScenes = 0

			var words = 0
			tree.forEach { node ->
				if (node.value.type == SceneItem.Type.Scene) {
					val count = sceneEditorRepository.countWordsInScene(node.value)
					words += count
					++numScenes
				}
			}

			yield()

			val wordsByChapter = mutableMapOf<String, Int>()
			tree.children.forEach { node ->
				val chapterName = node.value.name
				var wordsInChapter = 0
				node.forEach { child ->
					if (child.value.type == SceneItem.Type.Scene) {
						val count = sceneEditorRepository.countWordsInScene(child.value)
						wordsInChapter += count
					}
				}

				wordsByChapter[chapterName] = wordsInChapter
			}

			yield()

			encyclopediaRepository.loadEntries()
			val entriesByType = mutableMapOf<EntryType, Int>()
			encyclopediaRepository.entryListFlow.take(1).collect { entries ->
				EntryType.entries.forEach { type ->
					val numEntriesOfType = entries.count { it.type == type }
					entriesByType[type] = numEntriesOfType
				}
			}

			yield()

			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(
						created = created,
						numberOfScenes = numScenes,
						totalWords = words,
						wordsByChapter = wordsByChapter,
						encyclopediaEntriesByType = entriesByType,
						hasServer = globalSettingsRepository.serverSettings != null,
						isLoadingStats = false
					)
				}
			}
		}
	}

	override fun isAtRoot() = true
	override fun shouldConfirmClose() = emptySet<CloseConfirm>()
	override fun getExportStoryFileName() = sceneEditorRepository.getExportStoryFileName()

	override fun showProjectStats() = contentRouter.showProjectStats()
	override fun showProjectSettings() = contentRouter.showProjectSettings()
	override fun onBack() = contentRouter.onBack()
}

val wordRegex = Regex("""(\s+|(\r\n|\r|\n))""")
fun SceneEditorRepository.countWordsInScene(sceneItem: SceneItem): Int {
	val markdown = loadSceneMarkdownRaw(sceneItem)
	val count = wordRegex.findAll(markdown.trim()).count()
	return count
}
