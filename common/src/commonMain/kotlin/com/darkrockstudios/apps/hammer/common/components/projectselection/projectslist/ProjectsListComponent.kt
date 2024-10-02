package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectsSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncAccLogW
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.lifecycleCoroutineScope
import io.github.aakira.napier.Napier
import korlibs.datastructure.iterators.parallelMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.collections.set

class ProjectsListComponent(
	componentContext: ComponentContext,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectsList,
	SavableComponent<ProjectsList.State>(componentContext),
	ComponentToaster by ComponentToasterImpl() {
	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val projectsSynchronizer: ClientProjectsSynchronizer by inject()
	private val networkConnectivity: NetworkConnectivity by inject()
	private val projectMetadataDatasource: ProjectMetadataDatasource by inject()
	private val strRes: StrRes by inject()
	private val clock: Clock by inject()

	private var loadProjectsJob: Job? = null
	private var syncProjectsJob: Job? = null
	private var syncScope: CoroutineScope? = null

	private val _state by savableState {
		ProjectsList.State(
			projects = emptyList(),
			projectsPath = HPath(globalSettingsRepository.globalSettings.projectsDirectory, "", true),
			isServerSynced = projectsSynchronizer.isServerSynchronized(),
		)
	}
	override val state: Value<ProjectsList.State> = _state
	override fun getStateSerializer() = ProjectsList.State.serializer()

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					val oldPath = state.value.projectsPath
					_state.getAndUpdate {
						val projectsPath = settings.projectsDirectory.toPath().toHPath()
						it.copy(
							projectsPath = projectsPath,
						)
					}

					if (oldPath.path != settings.projectsDirectory) {
						loadProjectList()
					}
				}
			}
		}

		scope.launch {
			globalSettingsRepository.serverSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							isServerSynced = settings?.url != null,
						)
					}
				}
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		watchSettingsUpdates()
		initialProjectSync()
	}

	override fun onResume() {
		super.onResume()
		loadProjectList()
	}

	private fun initialProjectSync() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.first().let { settings ->
				if (
					projectsSynchronizer.isServerSynchronized() &&
					settings.automaticSyncing &&
					networkConnectivity.hasActiveConnection() &&
					projectsSynchronizer.initialSync.not()
				) {
					projectsSynchronizer.initialSync = true
					showProjectsSync()
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()

		_state.getAndUpdate {
			it.copy(
				projectsPath = HPath(globalSettingsRepository.globalSettings.projectsDirectory, "", true),
				isServerSynced = projectsSynchronizer.isServerSynchronized()
			)
		}
	}

	override fun loadProjectList() {
		val projectsDir = HPath(
			path = globalSettingsRepository.globalSettings.projectsDirectory,
			name = "",
			isAbsolute = true
		)

		loadProjectsJob?.cancel()
		loadProjectsJob = scope.launch {
			val projects = projectsRepository.getProjects(projectsDir)
			val projectData = projects.mapNotNull { projectDef ->
				val metadata = projectMetadataDatasource.loadMetadata(projectDef)
				if (metadata != null) {
					ProjectData(projectDef, metadata)
				} else {
					Napier.w { "Failed to load metadata for project: ${projectDef.name}" }
					null
				}
			}.sortedByDescending { it.metadata.info.lastAccessed }

			withContext(dispatcherMain) {
				_state.getAndUpdate { it.copy(projects = projectData) }
				loadProjectsJob = null
			}
		}
	}

	private fun updateLastAccessed(projectDef: ProjectDef) {
		projectMetadataDatasource.updateMetadata(projectDef) { metadata ->
			metadata.copy(
				info = metadata.info.copy(
					lastAccessed = clock.now()
				)
			)
		}
	}

	override fun selectProject(projectDef: ProjectDef) {
		updateLastAccessed(projectDef)
		onProjectSelected(projectDef)
	}

	override fun showCreate() {
		_state.getAndUpdate { it.copy(showCreateDialog = true) }
	}

	override fun hideCreate() {
		_state.getAndUpdate {
			it.copy(
				showCreateDialog = false,
				createDialogProjectName = ""
			)
		}
	}

	override fun createProject(projectName: String) {
		val result = projectsRepository.createProject(projectName)
		if (isSuccess(result)) {
			if (projectsSynchronizer.isServerSynchronized()) {
				projectsSynchronizer.createProject(projectName)
			}
			Napier.i("Project created: $projectName")
			loadProjectList()
			hideCreate()
		} else {
			result.displayMessage?.let { msg ->
				showToast(scope, msg)
			}

			Napier.e("Failed to create Project: $projectName")
		}
	}

	override fun deleteProject(projectDef: ProjectDef) {
		val projectId = projectsRepository.getProjectId(projectDef)
		val syncedProject = if (projectId != null) {
			SyncedProjectDefinition(projectDef, projectId)
		} else {
			null
		}

		if (projectsRepository.deleteProject(projectDef)) {
			Napier.i("Project deleted: ${projectDef.name}")
			if (syncedProject != null) {
				projectsSynchronizer.deleteProject(syncedProject)
			}

			loadProjectList()
		}
	}

	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata {
		return projectMetadataDatasource.loadMetadata(projectDef)
	}

	override fun onProjectNameUpdate(newProjectName: String) {
		_state.getAndUpdate { it.copy(createDialogProjectName = newProjectName) }
	}

	private suspend fun syncProject(
		projectDef: ProjectDef,
		onLog: OnSyncLog,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit
	): Boolean {
		onLog(syncLogI(strRes.get(MR.strings.sync_log_begin_project, projectDef.name), projectDef))

		var success = false
		temporaryProjectTask(projectDef) { projScope ->
			val synchronizer: ClientProjectSynchronizer = projScope.get { parametersOf(projectDef) }
			success = synchronizer.sync(
				onProgress = onProgress,
				onLog = { message -> onLog(message) },
				onConflict = {
					onLog(
						syncLogW(
							strRes.get(MR.strings.sync_log_project_conflict, projectDef.name),
							projectDef
						)
					)
					throw IllegalStateException("Entity conflict must be handled by Project sync")
				},
				onComplete = {}
			)
		}

		return success
	}

	private fun syncNewProjectStatus(projects: List<ProjectDef>) {
		val newStatuses = mutableMapOf<String, ProjectsList.ProjectSyncStatus>()
		projects.forEach { projDef ->
			newStatuses[projDef.name] = ProjectsList.ProjectSyncStatus(projectName = projDef.name)
		}

		_state.getAndUpdate {
			it.copy(
				syncState = it.syncState.copy(
					projectsStatus = newStatuses
				)
			)
		}
	}

	private suspend fun syncProgressStatus(projectName: String, status: ProjectsList.Status, progress: Float? = null) =
		withContext(mainDispatcher) {
			_state.getAndUpdate {
				val projStatus = it.syncState.projectsStatus[projectName]!!

				val map = it.syncState.projectsStatus.toMutableMap()
				val updatedMap = projStatus.copy(
					status = status,
					progress = progress ?: projStatus.progress
				)
				map[projectName] = updatedMap

				it.copy(
					syncState = it.syncState.copy(
						projectsStatus = map
					)
				)
			}
		}

	override fun syncProjects(callback: (Boolean) -> Unit) {
		syncProjectsJob?.cancel(CancellationException("Started another sync"))
		syncScope?.cancel(CancellationException("Started another sync"))
		val newScope = lifecycleCoroutineScope(dispatcherDefault)
		syncScope = newScope

		syncProjectsJob = newScope.launch {
			var projects = projectsRepository.getProjects()
			syncNewProjectStatus(projects)

			onSyncLog(syncAccLogI(strRes.get(MR.strings.sync_log_begin_account)))

			val success = projectsSynchronizer.syncProjects(::onSyncLog)

			yield()

			var allSuccess = success
			if (success) {
				onSyncLog(syncAccLogI(strRes.get(MR.strings.sync_log_begin_projects)))

				projects = projectsRepository.getProjects()
				syncNewProjectStatus(projects)

				projects.parallelMap { projectDef ->
					newScope.launch {
						syncProgressStatus(projectDef.name, ProjectsList.Status.Syncing)

						suspend fun onProgress(progress: Float, message: SyncLogMessage?) {
							syncProgressStatus(projectDef.name, ProjectsList.Status.Syncing, progress)
							if (message != null) onSyncLog(message)
						}

						val projectSuccess = syncProject(projectDef, ::onSyncLog, ::onProgress)
						allSuccess = allSuccess && projectSuccess

						val newStatus = if (projectSuccess) ProjectsList.Status.Complete else ProjectsList.Status.Failed
						syncProgressStatus(projectDef.name, newStatus)
					}
				}.joinAll()
			} else {
				projects.forEach { projectDef ->
					syncProgressStatus(projectDef.name, ProjectsList.Status.Failed)
				}
			}

			callback(allSuccess)

			onSyncLog(syncAccLogI(strRes.get(MR.strings.sync_log_end_projects)))

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						syncState = it.syncState.copy(
							syncComplete = true
						)
					)
				}

				loadProjectList()

				if (allSuccess && globalSettingsRepository.globalSettings.autoCloseSyncDialog) {
					hideProjectsSync()
				}
			}
		}
	}

	override fun cancelProjectsSync() {
		syncProjectsJob?.cancel(CancellationException("User canceled sync"))
		syncProjectsJob = null

		syncScope?.cancel(CancellationException("User canceled sync"))

		scope.launch(mainDispatcher) {
			onSyncLog(syncAccLogW("Sync canceled by user"))

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					val statuses = it.syncState.projectsStatus.toMutableMap()
					it.syncState.projectsStatus.values.forEach { status ->
						if (status.status == ProjectsList.Status.Syncing || status.status == ProjectsList.Status.Pending) {
							statuses[status.projectName] = status.copy(
								status = ProjectsList.Status.Canceled
							)
						}
					}

					it.copy(
						syncState = it.syncState.copy(
							syncComplete = true,
							projectsStatus = statuses
						),
					)
				}
			}
		}
	}

	override fun hideProjectsSync() {
		resetSync()
	}

	override fun showProjectsSync() {
		_state.getAndUpdate {
			it.copy(
				syncState = it.syncState.copy(
					showProjectSync = true
				),
			)
		}

		scope.launch {
			syncProjects { success ->
				if (success) {
					showToast(scope, MR.strings.projects_list_toast_sync_complete)
				} else {
					showToast(scope, MR.strings.projects_list_toast_sync_failed)
				}
			}
		}
	}

	private suspend fun onSyncLog(syncLog: SyncLogMessage) {
		Napier.i(syncLog.message)
		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					syncState = it.syncState.copy(
						syncLog = it.syncState.syncLog + syncLog
					),
				)
			}
		}
	}

	private fun resetSync() {
		_state.getAndUpdate {
			it.copy(
				syncState = ProjectsList.SyncState()
			)
		}
	}
}