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
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_success
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()
	private val statisticsService: StatisticsService by projectInject()

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

		subscribeToStats()
		loadData()
		listenForSyncEvents()
	}

	private fun subscribeToStats() {
		scope.launch {
			statisticsService.statsFlow.collect { stats ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							numberOfScenes = stats.numberOfScenes,
							totalWords = stats.totalWords,
							wordsByChapter = stats.wordsByChapter,
							encyclopediaEntriesByType = stats.encyclopediaEntriesByType
								.mapKeys { (key, _) -> EntryType.valueOf(key) },
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
		}
	}

	override fun isAtRoot() = true
	override fun shouldConfirmClose() = emptySet<CloseConfirm>()
	override fun getExportStoryFileName() = sceneEditorRepository.getExportStoryFileName()

	override fun showProjectStats() = contentRouter.showProjectStats()
	override fun showProjectSettings() = contentRouter.showProjectSettings()
	override fun onBack() = contentRouter.onBack()
}
