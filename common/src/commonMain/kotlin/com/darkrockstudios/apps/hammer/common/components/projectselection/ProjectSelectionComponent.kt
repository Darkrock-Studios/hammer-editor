package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.accountrepository.AccountRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectsSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.koin.core.component.get
import org.koin.core.component.getScopeId
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class ProjectSelectionComponent(
	componentContext: ComponentContext,
	override val showProjectDirectory: Boolean = false,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {

	private val mainDispatcher by injectMainDispatcher()

	private val accountRepository: AccountRepository by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val projectsSynchronizer: ClientProjectsSynchronizer by inject()
	private var loadProjectsJob: Job? = null

	private val _state = MutableValue(
		ProjectSelection.State(
			projectsDir = projectsRepository.getProjectsDirectory(),
			projects = emptyList(),
			uiTheme = globalSettingsRepository.globalSettings.uiTheme
		)
	)
	override val state: Value<ProjectSelection.State> = _state

	init {
		watchSettingsUpdates()

		if (exampleProjectRepository.shouldInstallFirstTime()) {
			exampleProjectRepository.install()
		}
	}

	override fun onCreate() {
		super.onCreate()
		loadProjectList()
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.reduce {
						val projectsPath = settings.projectsDirectory.toPath().toHPath()
						it.copy(
							projectsDir = projectsPath,
							uiTheme = settings.uiTheme
						)
					}
				}
			}
		}

		scope.launch {
			globalSettingsRepository.serverSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.reduce {
						it.copy(
							serverUrl = settings?.url
						)
					}
				}
			}
		}
	}

	override fun loadProjectList() {
		loadProjectsJob?.cancel()
		loadProjectsJob = scope.launch {
			val projects = projectsRepository.getProjects(state.value.projectsDir)
			val projectData = projects.mapNotNull { projectDef ->
				val metadata = projectsRepository.loadMetadata(projectDef)
				if (metadata != null) {
					ProjectData(projectDef, metadata)
				} else {
					Napier.w { "Failed to load metadata for project: ${projectDef.name}" }
					null
				}
			}

			withContext(dispatcherMain) {
				_state.reduce {
					it.copy(projects = projectData)
				}
				loadProjectsJob = null
			}
		}
	}

	override fun setProjectsDir(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)

		val curSettings = globalSettingsRepository.globalSettings
		val updatedSettings = curSettings.copy(projectsDirectory = path)
		globalSettingsRepository.updateSettings(updatedSettings)

		projectsRepository.getProjectsDirectory()
		_state.reduce {
			it.copy(projectsDir = hpath)
		}
		loadProjectList()
	}

	override fun selectProject(projectDef: ProjectDef) = onProjectSelected(projectDef)

	override fun createProject(projectName: String) {
		if (projectsRepository.createProject(projectName)) {
			if (projectsSynchronizer.isServerSynchronized()) {
				projectsSynchronizer.createProject(projectName)
			}
			Napier.i("Project created: $projectName")
			loadProjectList()
		} else {
			Napier.e("Failed to create Project: $projectName")
		}
	}

	override fun deleteProject(projectDef: ProjectDef) {
		if (projectsRepository.deleteProject(projectDef)) {
			Napier.i("Project deleted: ${projectDef.name}")
			if (projectsSynchronizer.isServerSynchronized()) {
				projectsSynchronizer.deleteProject(projectDef)
			}

			loadProjectList()
		}
	}

	override fun showLocation(location: ProjectSelection.Locations) {
		_state.reduce {
			it.copy(location = location)
		}
	}

	override fun setUiTheme(theme: UiTheme) {
		val settings = globalSettingsRepository.globalSettings.copy(
			uiTheme = theme
		)
		globalSettingsRepository.updateSettings(settings)
	}

	override suspend fun reinstallExampleProject() {
		exampleProjectRepository.install()
		loadProjectList()
	}

	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? {
		return projectsRepository.loadMetadata(projectDef)
	}

	override fun beginSetupServer() {
		_state.reduce {
			it.copy(
				serverSetup = true
			)
		}
	}

	override fun cancelServerSetup() {
		_state.reduce {
			it.copy(
				serverSetup = false
			)
		}
	}

	override suspend fun authTest() {
		accountRepository.testAuth()
	}

	override fun removeServer() {
		globalSettingsRepository.deleteServerSettings()
	}

	private fun resetSync() {
		_state.reduce {
			it.copy(
				syncState = ProjectSelection.SycState()
			)
		}
	}

	private suspend fun syncProject(projectDef: ProjectDef, onLog: suspend (String) -> Unit): Boolean {
		onLog("Syncing Project: ${projectDef.name}")

		val projScope = ProjectDefScope(projectDef)

		val projectEditor: ProjectEditorRepository = projScope.get { parametersOf(projectDef) }
		projectEditor.initializeProjectEditor()

		val notesEditor: NotesRepository = projScope.get { parametersOf(projectDef) }

		val synchronizer: ClientProjectSynchronizer = projScope.get { parametersOf(projectDef) }
		val success = synchronizer.sync(
			onProgress = { percent, message -> message?.let { onLog(it) } },
			onLog = { message -> message?.let { onLog(it) } },
			onConflict = {
				onLog("There is a conflict in project: ${projectDef.name}, open that project and sync in order to resolve it")
				throw IllegalStateException("Entity conflict must be handled by Project sync")
			},
			onComplete = {}
		)

		projectEditor.close()
		notesEditor.close()
		projScope.closeScope()
		getKoin().deleteScope(projScope.getScopeId())

		return success
	}

	override fun syncProjects(callback: (Boolean) -> Unit) {
		scope.launch {
			val success = projectsSynchronizer.syncProjects(::onSyncLog)

			var allSuccess = success
			if (success) {
				val projects = projectsRepository.getProjects()
				projects.forEach { projectName ->
					allSuccess = allSuccess && syncProject(projectName, ::onSyncLog)
				}
			}

			callback(allSuccess)

			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						syncState = it.syncState.copy(
							syncComplete = true
						)
					)
				}
			}

			loadProjectList()
		}
	}

	override fun hideProjectsSync() {
		resetSync()
	}

	override fun showProjectsSync() {
		_state.reduce {
			it.copy(
				syncState = it.syncState.copy(
					showProjectSync = true
				),
			)
		}
	}

	private suspend fun onSyncLog(message: String) {
		Napier.i(message)
		withContext(mainDispatcher) {
			_state.reduce {
				it.copy(
					syncState = it.syncState.copy(
						syncLog = it.syncState.syncLog + message
					),
				)
			}
		}
	}

	override suspend fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean
	): Result<Boolean> {
		if (state.value.serverError != null) {
			_state.reduce {
				it.copy(
					serverError = null
				)
			}
		}

		val cleanUrl = cleanUpUrl(url)
		return if (validateUrl(cleanUrl).not()) {
			val message = "Invalid URL"
			_state.reduce {
				it.copy(
					serverUrl = null,
					serverError = message
				)
			}
			Result.failure(Exception(message))
		} else {
			val result = accountRepository.setupServer(ssl, cleanUrl, email, password, create)
			if (result.isSuccess) {
				_state.reduce {
					it.copy(
						serverUrl = cleanUrl,
						serverSetup = false
					)
				}
			} else {
				_state.reduce {
					it.copy(
						serverUrl = null,
						serverError = result.exceptionOrNull()?.message ?: "Unknown error"
					)
				}
			}

			result
		}
	}

	companion object {
		// regex to validate url with port number
		private val urlWithPortRegex =
			Regex("""^(?:w{1,3}\.)?[^\s.]+(?:\.[a-z]+)*(?::\d+)?(?![^<]*(?:</\w+>|/?>))$""")

		fun validateUrl(url: String): Boolean {
			return url.isNotBlank() && urlWithPortRegex.matches(url)
		}

		fun cleanUpUrl(url: String): String {
			var cleanUrl: String = url.trim()
			cleanUrl = cleanUrl.removeSuffix("http://")
			cleanUrl = cleanUrl.removeSuffix("https://")
			cleanUrl = cleanUrl.removeSuffix("/")

			return cleanUrl
		}
	}
}