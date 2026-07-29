package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporterRegistry
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogW
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.lifecycleCoroutineScope
import com.darkrockstudios.apps.hammer.project_home_action_import_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_import_toast_success
import com.darkrockstudios.apps.hammer.projects_list_toast_sync_complete
import com.darkrockstudios.apps.hammer.projects_list_toast_sync_failed
import com.darkrockstudios.apps.hammer.sync_log_begin_account
import com.darkrockstudios.apps.hammer.sync_log_begin_project
import com.darkrockstudios.apps.hammer.sync_log_begin_projects
import com.darkrockstudios.apps.hammer.sync_log_end_projects
import com.darkrockstudios.apps.hammer.sync_log_project_conflict
import io.github.aakira.napier.Napier
import korlibs.datastructure.iterators.parallelMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData as StoredData

class ProjectsListComponent(
	componentContext: ComponentContext,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectsList,
	SavableComponent<ProjectsList.State>(componentContext),
	ComponentToaster by ComponentToasterImpl() {
	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val projectsSynchronizer: ClientAccountSynchronizer by inject()
	private val protocolMismatchRepository: ProtocolMismatchRepository by inject()
	private val networkConnectivity: NetworkConnectivity by inject()
	private val projectMetadataDatasource: ProjectMetadataDatasource by inject()
	private val statisticsCacheReader: ProjectStatisticsCacheReader by inject()
	private val importerRegistry: StoryImporterRegistry by inject()
	private val fileSystem: FileSystem by inject()
	private val toml: Toml by inject()
	private val strRes: StrRes by inject()
	private val clock: Clock by inject()

	private var loadProjectsJob: Job? = null
	private var syncProjectsJob: Job? = null
	private var importPreviewJob: Job? = null
	private val importPreviewLock = Mutex()
	private var syncScope: CoroutineScope? = null

	private val modalRouter = ProjectListModalRouter(
		componentContext = componentContext,
		onReauthSuccess = ::showProjectsSync,
	)
	override val modalRouterState = modalRouter.state

	private val _state by savableState {
		ProjectsList.State(
			projects = emptyList(),
			projectsPath = HPath(globalSettingsStore.globalSettings.projectsDirectory, "", true),
			isServerSynced = projectsSynchronizer.isServerSynchronized(),
		)
	}
	override val state: Value<ProjectsList.State> = _state
	override fun getStateSerializer() = ProjectsList.State.serializer()

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsStore.globalSettingsUpdates.collect { settings ->
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
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
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
		watchProtocolMismatch()
	}

	private fun watchProtocolMismatch() {
		scope.launch {
			protocolMismatchRepository.mismatches.collect { info ->
				withContext(mainDispatcher) {
					modalRouter.showProtocolMismatch(info)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		loadProjectList()
	}

	private fun initialProjectSync() {
		scope.launch {
			globalSettingsStore.globalSettingsUpdates.first().let { settings ->
				if (
					projectsSynchronizer.isServerSynchronized() &&
					settings.automaticSyncing &&
					networkConnectivity.hasActiveConnection() &&
					projectsSynchronizer.initialSync.not()
				) {
					projectsSynchronizer.initialSync = true
					withContext(mainDispatcher) {
						showProjectsSync()
					}
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()

		_state.getAndUpdate {
			it.copy(
				projectsPath = HPath(
					globalSettingsStore.globalSettings.projectsDirectory,
					"",
					true
				),
				isServerSynced = projectsSynchronizer.isServerSynchronized()
			)
		}
	}

	override fun loadProjectList() {
		val projectsDir = HPath(
			path = globalSettingsStore.globalSettings.projectsDirectory,
			name = "",
			isAbsolute = true
		)

		loadProjectsJob?.cancel()
		loadProjectsJob = scope.launch {
			val projects = projectsRepository.getProjects(projectsDir)
			val projectData = projects.parallelMap { projectDef ->
				// The project can be deleted concurrently
				val metadata = try {
					projectMetadataDatasource.loadMetadata(projectDef)
				} catch (_: IOException) {
					null
				}
				if (metadata != null) {
					ProjectData(
						definition = projectDef,
						metadata = metadata,
						storedData = loadStoredProjectData(projectDef),
						totalWords = statisticsCacheReader.loadTotalWords(projectDef),
					)
				} else {
					Napier.d { "Failed to load metadata for project: ${projectDef.name}" }
					null
				}
			}.filterNotNull().sortedByDescending { it.metadata.info.lastAccessed }

			withContext(dispatcherMain) {
				_state.getAndUpdate { it.copy(projects = projectData) }
				loadProjectsJob = null
			}
		}
	}

	/**
	 * Reads `project_data.toml` (author, theme, word-count goal) without
	 * opening a per-project Koin scope — the projects list previews many
	 * projects and a full scope per row would be wasteful.
	 */
	private fun loadStoredProjectData(projectDef: ProjectDef): StoredData {
		val path = projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME
		return fileSystem.readTomlOrNull<StoredProjectData>(path, toml) { e ->
			//Napier.d("Failed to read stored project data for ${projectDef.name}, using defaults", e)
		}?.data ?: StoredData()
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
		modalRouter.showProjectCreate()
	}

	override fun hideCreate() {
		_state.getAndUpdate {
			it.copy(createDialogProjectName = "")
		}
		modalRouter.dismissProjectCreate()
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

	override fun renameProject(projectDef: ProjectDef, newName: String) {
		val projectId = projectsRepository.getProjectId(projectDef)
		if (projectsRepository.renameProject(projectDef, newName).isSuccess) {
			Napier.i("Project renamed: ${projectDef.name}")
			if (projectId != null) {
				projectsSynchronizer.renameProject(projectId, newName)
			}

			loadProjectList()
		} else {
			Napier.e("Failed to rename Project: ${projectDef.name} to '$newName'")
		}
	}

	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata {
		return projectMetadataDatasource.loadMetadata(projectDef)
	}

	override fun onProjectNameUpdate(newProjectName: String) {
		_state.getAndUpdate { it.copy(createDialogProjectName = newProjectName) }
	}

	override fun beginProjectImport() {
		modalRouter.dismissProjectCreate()
		_state.getAndUpdate {
			it.copy(createDialogProjectName = "", showImportFilePicker = true)
		}
	}

	override fun cancelImportFilePicker() {
		_state.getAndUpdate { it.copy(showImportFilePicker = false) }
	}

	// Parsing a full manuscript is not instant, and both entry points are called from the UI
	// thread, so the preview is always built off it and applied when it lands.
	override fun selectImportFile(name: String, content: ByteArray) {
		val sourceName = name.substringBeforeLast('.')
		val format = importerRegistry.formatForFileName(name)
		val initialOptions = ImportOptions(format = format)

		_state.getAndUpdate { it.copy(showImportFilePicker = false) }

		launchPreview {
			val preview = buildPreview(sourceName, content, initialOptions) ?: return@launchPreview
			val projectName = preview.title?.takeIf { it.isNotBlank() } ?: sourceName
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						showImportDialog = true,
						importOptions = initialOptions,
						importSourceName = sourceName,
						importProjectName = projectName,
						importFileContent = content,
						importPreview = preview,
					)
				}
			}
		}
	}

	override fun updateImportProjectName(name: String) {
		_state.getAndUpdate { it.copy(importProjectName = name) }
	}

	override fun updateImportOptions(options: ImportOptions) {
		val current = _state.value
		// Show the new selection immediately; the re-parsed preview follows.
		_state.getAndUpdate { it.copy(importOptions = options) }

		launchPreview {
			// Free-text options (the RTF chapter pattern) change per keystroke; settle first so a
			// burst of edits costs one parse rather than one per character.
			delay(PREVIEW_DEBOUNCE_MS)
			val preview = buildPreview(
				sourceName = current.importSourceName,
				content = current.importFileContent,
				options = options,
			) ?: return@launchPreview
			withContext(mainDispatcher) {
				_state.getAndUpdate { it.copy(importPreview = preview) }
			}
		}
	}

	private fun launchPreview(block: suspend CoroutineScope.() -> Unit) {
		importPreviewJob?.cancel()
		importPreviewJob = scope.launch(block = block)
	}

	/**
	 * Parses off the UI thread, returning null when the import fails or the caller was cancelled.
	 * The importers hand off to third-party parsers that can throw on a malformed file, and an
	 * escaping throw would take the component's whole scope down with it.
	 */
	private suspend fun buildPreview(
		sourceName: String,
		content: ByteArray,
		options: ImportOptions,
	): ImportPreview? {
		// The parse never suspends, so cancelling only takes effect around it; hold the lock so a
		// superseded parse can't run alongside its replacement.
		val preview = importPreviewLock.withLock {
			coroutineContext.ensureActive()
			withContext(dispatcherDefault) {
				try {
					importerRegistry.forFormat(options.format).preview(sourceName, content, options)
				} catch (e: CancellationException) {
					throw e
					// Import can fail many ways (parse, malformed file); report and show failure toast.
				} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
					Napier.e("Import: failed to parse '$sourceName'", e)
					null
				}
			}
		}
		if (preview == null) {
			withContext(mainDispatcher) {
				_state.getAndUpdate { it.copy(importPreview = ImportPreview(emptyList())) }
				showToast(scope, Res.string.project_home_action_import_toast_failure)
			}
		}
		return preview
	}

	override fun cancelImportDialog() {
		importPreviewJob?.cancel()
		_state.getAndUpdate {
			it.copy(
				showImportDialog = false,
				importFileContent = ByteArray(0),
				importSourceName = "",
				importProjectName = "",
				importPreview = ImportPreview(emptyList()),
			)
		}
	}

	override suspend fun confirmImportDialog() {
		// A re-parse triggered by a last-moment option change must not land after we snapshot.
		importPreviewJob?.join()
		val projectName = _state.value.importProjectName
		val previewToImport = _state.value.importPreview

		val result = projectsRepository.createProject(projectName)
		if (!isSuccess(result)) {
			result.displayMessage?.let { msg -> showToast(scope, msg) }
			Napier.e("Import: failed to create project '$projectName'")
			return
		}
		val newDef = result.data

		if (projectsSynchronizer.isServerSynchronized()) {
			projectsSynchronizer.createProject(projectName)
		}

		_state.getAndUpdate {
			it.copy(
				showImportDialog = false,
				importFileContent = ByteArray(0),
				importSourceName = "",
				importProjectName = "",
				importPreview = ImportPreview(emptyList()),
			)
		}

		try {
			withContext(dispatcherDefault) {
				temporaryProjectTask(newDef) { projScope ->
					val importStoryUseCase: ImportStoryUseCase = projScope.get()
					importStoryUseCase.execute(previewToImport)
				}
			}
			loadProjectList()
			withContext(mainDispatcher) {
				showToast(scope, Res.string.project_home_action_import_toast_success)
			}
			// Import can fail many ways (parse, IO); report and show failure toast.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Import failed", e)
			withContext(mainDispatcher) {
				showToast(scope, Res.string.project_home_action_import_toast_failure)
			}
		}
	}

	private suspend fun syncProject(
		projectDef: ProjectDef,
		onLog: OnSyncLog,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit
	): ProjectSyncOutcome {
		onLog(syncLogI(strRes.get(Res.string.sync_log_begin_project, projectDef.name), projectDef))

		var success = false
		var conflicted = false

		try {
			temporaryProjectTask(projectDef) { projScope ->
				val synchronizer: ClientProjectSynchronizer =
					projScope.get { parametersOf(projectDef) }
				val conflictBroker: ProjectDataConflictBroker =
					projScope.get { parametersOf(projectDef) }

				coroutineScope {
					// Bulk account sync has no interactive resolver. A project-data conflict reports
					// to the broker and waits on resolutions forever, leaving the project stuck
					// "Syncing". Watch for it and abort so the project stops instead of hanging.
					val conflictWatcher = launch {
						for (conflict in conflictBroker.conflicts) {
							onLog(
								syncLogW(
									strRes.get(
										Res.string.sync_log_project_conflict,
										projectDef.name
									),
									projectDef
								)
							)
							conflicted = true
							conflictBroker.abort()
						}
					}

					try {
						success = synchronizer.sync(
							onProgress = onProgress,
							onLog = { message -> onLog(message) },
							onConflict = {
								onLog(
									syncLogW(
										strRes.get(
											Res.string.sync_log_project_conflict,
											projectDef.name
										),
										projectDef
									)
								)
								conflicted = true
								throw IllegalStateException("Entity conflict must be handled by Project sync")
							},
							onComplete = {},
							onUnauthorized = ::showReauth
						)
					} finally {
						conflictWatcher.cancel()
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// A conflict aborts the entity sync by throwing; that's a resolvable state, not a failure.
			if (!conflicted) throw e
		}

		return when {
			success -> ProjectSyncOutcome.Success
			conflicted -> ProjectSyncOutcome.NeedsResolution
			else -> ProjectSyncOutcome.Failed
		}
	}

	private enum class ProjectSyncOutcome { Success, NeedsResolution, Failed }

	private suspend fun syncNewProjectStatus(projects: List<ProjectDef>) {
		val newStatuses = mutableMapOf<String, ProjectsList.ProjectSyncStatus>()
		projects.forEach { projDef ->
			newStatuses[projDef.name] = ProjectsList.ProjectSyncStatus(projectName = projDef.name)
		}

		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					syncState = it.syncState.copy(
						syncComplete = false,
						projectsStatus = newStatuses
					)
				)
			}
		}
	}

	private suspend fun syncProgressStatus(
		projectName: String,
		status: ProjectsList.Status,
		progress: Float? = null
	) =
		withContext(mainDispatcher) {
			_state.getAndUpdate {
				val projStatus =
					it.syncState.projectsStatus[projectName]
						?: error("Project status not found for $projectName")

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

	private suspend fun showReauth() = withContext(mainDispatcher) {
		modalRouter.showServerReauthentication()
	}

	private var ideaConflictResponse: CompletableDeferred<StoryIdea?>? = null

	// Suspends the ideas sync phase until the user resolves the conflict in the sync dialog
	// (or the sync is canceled, which cancels the await).
	private suspend fun onIdeaConflict(conflict: IdeaConflict): StoryIdea? {
		val response = CompletableDeferred<StoryIdea?>()
		ideaConflictResponse = response
		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(syncState = it.syncState.copy(ideaConflict = conflict))
			}
		}
		return try {
			response.await()
		} finally {
			ideaConflictResponse = null
			withContext(NonCancellable + mainDispatcher) {
				_state.getAndUpdate {
					it.copy(syncState = it.syncState.copy(ideaConflict = null))
				}
			}
		}
	}

	override fun resolveIdeaConflict(resolution: StoryIdea?) {
		ideaConflictResponse?.complete(resolution)
	}

	override fun syncProjects(callback: (Boolean) -> Unit) {
		syncProjectsJob?.cancel(CancellationException("Started another sync"))
		syncScope?.cancel(CancellationException("Started another sync"))
		val newScope = lifecycleCoroutineScope(dispatcherDefault)
		syncScope = newScope

		syncProjectsJob = newScope.launch {
			try {
				var projects = projectsRepository.getProjects()
				syncNewProjectStatus(projects)

				onSyncLog(syncAccLogI(strRes.get(Res.string.sync_log_begin_account)))

				var ideasSuccess = true
				val success = projectsSynchronizer.syncProjects(
					onLog = ::onSyncLog,
					onUnauthorized = ::showReauth,
					onIdeaConflict = ::onIdeaConflict,
					onIdeasSyncResult = { ideasSuccess = it },
				)

				yield()

				var allSuccess = success && ideasSuccess
				if (success) {
					onSyncLog(syncAccLogI(strRes.get(Res.string.sync_log_begin_projects)))

					projects = projectsRepository.getProjects()
					syncNewProjectStatus(projects)

					// Only sync projects that have a serverProjectId assigned
					val projectsToSync = projects.mapNotNull { projectDef ->
						val metadata = projectMetadataDatasource.loadMetadata(projectDef)
						val serverProjectId = metadata.info.serverProjectId
						if (serverProjectId == null) {
							Napier.w { "Skipping project sync for '${projectDef.name}' - no server project ID yet" }
							syncProgressStatus(projectDef.name, ProjectsList.Status.Pending)
							null
						} else {
							SyncedProjectDefinition(projectDef, serverProjectId)
						}
					}

					// Pre-sync change probe: skip projects the server confirms are unchanged.
					val unchangedProjectIds =
						projectsSynchronizer.probeUnchangedProjects(projectsToSync)

					projectsToSync.parallelMap { synced ->
						val projectDef = synced.projectDef
						newScope.launch {
							try {
								if (synced.projectId in unchangedProjectIds) {
									onSyncLog(syncLogI("No changes — skipped", projectDef))
									syncProgressStatus(
										projectDef.name,
										ProjectsList.Status.Complete
									)
									return@launch
								}

								syncProgressStatus(projectDef.name, ProjectsList.Status.Syncing)

								suspend fun onProgress(progress: Float, message: SyncLogMessage?) {
									syncProgressStatus(
										projectDef.name,
										ProjectsList.Status.Syncing,
										progress
									)
									if (message != null) onSyncLog(message)
								}

								val outcome = syncProject(projectDef, ::onSyncLog, ::onProgress)
								allSuccess = allSuccess && (outcome == ProjectSyncOutcome.Success)

								val newStatus = when (outcome) {
									ProjectSyncOutcome.Success -> ProjectsList.Status.Complete
									ProjectSyncOutcome.NeedsResolution -> ProjectsList.Status.NeedsResolution
									ProjectSyncOutcome.Failed -> ProjectsList.Status.Failed
								}
								syncProgressStatus(projectDef.name, newStatus)
							} catch (e: CancellationException) {
								throw e
							} catch (e: Exception) {
								Napier.e("Project sync failed for ${projectDef.name}", e)
								onSyncLog(
									syncLogE(
										"Sync failed: ${e.message ?: "Unknown error"}",
										projectDef
									)
								)
								allSuccess = false
								syncProgressStatus(projectDef.name, ProjectsList.Status.Failed)
							}
						}
					}.joinAll()
				} else {
					projects.forEach { projectDef ->
						syncProgressStatus(projectDef.name, ProjectsList.Status.Failed)
					}
				}

				callback(allSuccess)

				onSyncLog(syncAccLogI(strRes.get(Res.string.sync_log_end_projects)))

				withContext(mainDispatcher) {
					_state.getAndUpdate {
						it.copy(
							syncState = it.syncState.copy(
								syncComplete = true
							)
						)
					}

					loadProjectList()

					if (allSuccess && globalSettingsStore.globalSettings.autoCloseSyncDialog) {
						hideProjectsSync()
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Projects sync failed", e)
				onSyncLog(syncAccLogE("Sync failed: ${e.message ?: "Unknown error"}"))
				callback(false)

				withContext(NonCancellable + mainDispatcher) {
					_state.getAndUpdate {
						val statuses = it.syncState.projectsStatus.toMutableMap()
						it.syncState.projectsStatus.values.forEach { status ->
							if (status.status == ProjectsList.Status.Pending || status.status == ProjectsList.Status.Syncing) {
								statuses[status.projectName] =
									status.copy(status = ProjectsList.Status.Failed)
							}
						}

						it.copy(
							syncState = it.syncState.copy(
								syncComplete = true,
								projectsStatus = statuses,
							),
						)
					}

					loadProjectList()
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
		modalRouter.dismissProjectSync()
	}

	override fun showProjectsSync() {
		modalRouter.showProjectSync()

		scope.launch {
			syncProjects { success ->
				if (success) {
					showToast(scope, Res.string.projects_list_toast_sync_complete)
				} else {
					showToast(scope, Res.string.projects_list_toast_sync_failed)
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

	override fun showProjectRename(projectDef: ProjectDef) {
		modalRouter.showProjectRename(projectDef)
	}

	override fun dismissProjectRename() {
		modalRouter.dismissProjectRename()
	}

	override fun showProjectDelete(projectDef: ProjectDef) {
		modalRouter.showProjectDelete(projectDef)
	}

	override fun dismissProjectDelete() {
		modalRouter.dismissProjectDelete()
	}

	private companion object {
		const val PREVIEW_DEBOUNCE_MS = 150L
	}
}
