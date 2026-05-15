package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporter
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.EntryAppearance
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.deriveWritingStats
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.parseDailyWordTotals
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.component.inject
import kotlin.time.Clock

class ProjectHomeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val showProjectSync: () -> Unit,
	private val onShowGlobalSearch: () -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
	private val onShowScene: (SceneItem) -> Unit,
	private val onShowEntry: (EntryDef) -> Unit,
	private val onCloseProject: (() -> Unit)? = null,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome,
	ComponentToaster by ComponentToasterImpl() {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectBackupRepository: ProjectBackupRepository by inject()
	private val sceneEditorRepository: SceneEditorRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()
	private val statisticsService: StatisticsService by projectInject()
	private val tagIndexService: TagIndexService by projectInject()
	private val referenceIndexService: ReferenceIndexService by projectInject()
	private val importStoryUseCase: ImportStoryUseCase by projectInject()
	private val markdownImporter: StoryImporter by inject()

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

	override fun cancelExportDialog() {
		_state.getAndUpdate {
			it.copy(
				showExportDialog = false
			)
		}
	}

	override fun confirmExportDialog(options: ExportOptions) {
		_state.getAndUpdate {
			it.copy(
				showExportDialog = false,
				exportOptions = options,
				showExportFilePicker = true,
			)
		}
	}

	override fun endProjectExport() {
		_state.getAndUpdate {
			it.copy(
				showExportFilePicker = false
			)
		}
	}

	override fun beginProjectImport() {
		_state.getAndUpdate { it.copy(showImportFilePicker = true) }
	}

	override fun cancelImportFilePicker() {
		_state.getAndUpdate { it.copy(showImportFilePicker = false) }
	}

	override fun selectImportFile(name: String, content: String) {
		val sourceName = name.substringBeforeLast('.')
		val initialOptions = ImportOptions()
		val preview = markdownImporter.preview(sourceName, content, initialOptions)
		_state.getAndUpdate {
			it.copy(
				showImportFilePicker = false,
				showImportDialog = true,
				importOptions = initialOptions,
				importSourceName = sourceName,
				importFileContent = content,
				importPreview = preview,
			)
		}
	}

	override fun updateImportOptions(options: ImportOptions) {
		val current = _state.value
		val preview = markdownImporter.preview(
			sourceName = current.importSourceName,
			content = current.importFileContent,
			options = options,
		)
		_state.getAndUpdate {
			it.copy(importOptions = options, importPreview = preview)
		}
	}

	override fun cancelImportDialog() {
		_state.getAndUpdate {
			it.copy(
				showImportDialog = false,
				importFileContent = "",
				importSourceName = "",
				importPreview = ImportPreview(emptyList()),
			)
		}
	}

	override suspend fun confirmImportDialog() {
		val previewToImport = _state.value.importPreview
		_state.getAndUpdate {
			it.copy(
				showImportDialog = false,
				importFileContent = "",
				importSourceName = "",
				importPreview = ImportPreview(emptyList()),
			)
		}
		try {
			withContext(dispatcherDefault) {
				importStoryUseCase.execute(previewToImport)
			}
			withContext(mainDispatcher) {
				showToast(scope, ClientMessage.Resource(Res.string.project_home_action_import_toast_success))
			}
		} catch (e: Exception) {
			io.github.aakira.napier.Napier.e("Import failed", e)
			withContext(mainDispatcher) {
				showToast(scope, ClientMessage.Resource(Res.string.project_home_action_import_toast_failure))
			}
		}
	}

	override suspend fun exportProject(path: String, options: ExportOptions): HPath {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)
		val filePath = sceneEditorRepository.exportStory(hpath, options)

		withContext(mainDispatcher) {
			endProjectExport()
		}

		return filePath
	}

	override fun startProjectSync() = showProjectSync()

	override fun showGlobalSearch() = onShowGlobalSearch()

	override fun showGlobalSearchForTag(tag: String) = onShowGlobalSearchForTag(tag)

	override fun showLongestScene() {
		val id = _state.value.longestSceneId ?: return
		val sceneItem = sceneEditorRepository.getSceneItemFromId(id) ?: return
		onShowScene(sceneItem)
	}

	override fun showEntry(entry: EntryAppearance) {
		onShowEntry(
			EntryDef(
				projectDef = projectDef,
				id = entry.entryId,
				type = entry.type,
				name = entry.name,
			)
		)
	}

	override fun supportsBackup(): Boolean = projectBackupRepository.supportsBackup()

	override fun createBackup(callback: (ProjectBackupDef?) -> Unit) {
		scope.launch {
			val backup = projectBackupRepository.createBackup(projectDef)

			withContext(mainDispatcher) {
				callback(backup)

				val msg = if (backup != null) {
					ClientMessage.Resource(
						Res.string.project_home_action_backup_toast_success,
						backup.path.name
					)
				} else {
					ClientMessage.Resource(Res.string.project_home_action_backup_toast_failure)
				}
				showToast(scope, msg)
			}
		}
	}

	override fun onCreate() {
		super.onCreate()

		subscribeToStats()
		loadData()
		listenForSyncEvents()
	}

	private fun subscribeToStats() {
		scope.launch {
			statisticsService.statsFlow.collect { stats ->
				val dailyTotals = parseDailyWordTotals(stats.dailyWordTotals)
				val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
				val derived = deriveWritingStats(dailyTotals, today)
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							numberOfScenes = stats.numberOfScenes,
							totalWords = stats.totalWords,
							wordsByChapter = stats.wordsByChapter,
							encyclopediaEntriesByType = stats.encyclopediaEntriesByType
								.mapKeys { (key, _) -> EntryType.valueOf(key) },
							longestSceneId = stats.longestSceneId,
							longestSceneName = stats.longestSceneName,
							longestSceneWords = stats.longestSceneWords,
							shortestSceneWords = stats.shortestSceneWords,
							medianSceneWords = stats.medianSceneWords,
							sceneWordsStdDev = stats.sceneWordsStdDev,
							numberOfNotes = stats.numberOfNotes,
							numberOfTimelineEvents = stats.numberOfTimelineEvents,
							dailyWordTotals = dailyTotals,
							wordsPerDevice = stats.wordsPerDevice,
							topAppearances = stats.topAppearances,
							totalEntryConnections = stats.totalEntryConnections,
							wordCountGoal = stats.wordCountGoal,
							writingActivity = derived,
							hasServer = globalSettingsRepository.serverSettings != null,
							isLoadingStats = false
						)
					}
				}
			}
		}

		scope.launch {
			statisticsService.isDirty.collect { isDirty ->
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(isStatsDirty = isDirty) }
				}
			}
		}

		scope.launch {
			statisticsService.isCalculating.collect { isCalculating ->
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(isLoadingStats = isCalculating) }
				}
			}
		}

		scope.launch {
			tagIndexService.tagIndex.collect { index ->
				val breakdowns = index.toBreakdowns()
				val usesByType = index.totalUsesByType()
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(tagBreakdowns = breakdowns, tagUsesByType = usesByType)
					}
				}
			}
		}
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

			// Load metadata (created date) directly - not cached
			val metadata = sceneEditorRepository.getMetadata()
			val created = metadata.info.created.formatLocal("dd MMM `yy")

			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(created = created)
				}
			}

			// Load statistics from service (cached or calculated)
			statisticsService.loadStatistics()
		}
	}

	override fun refreshStatistics() {
		scope.launch {
			_state.getAndUpdate { it.copy(isLoadingStats = true) }
			statisticsService.recalculateStatistics()
			referenceIndexService.recalculate()
		}
	}

	override fun isAtRoot() = true
	override fun shouldConfirmClose() = emptySet<CloseConfirm>()
	override fun getExportStoryFileName() = sceneEditorRepository.getExportStoryFileName()

	override fun showProjectStats() = contentRouter.showProjectStats()
	override fun showProjectSettings() = contentRouter.showProjectSettings()

	override fun closeProject() {
		onCloseProject?.invoke()
	}

	override fun onBack() = contentRouter.onBack()
}
