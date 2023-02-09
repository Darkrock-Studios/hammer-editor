package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.koin.core.component.inject

class ProjectSelectionComponent(
	componentContext: ComponentContext,
	override val showProjectDirectory: Boolean = false,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {
	private val globalSettingsRepository by inject<GlobalSettingsRepository>()
	private val projectsRepository by inject<ProjectsRepository>()
	private val exampleProjectRepository by inject<ExampleProjectRepository>()
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
	}

	override fun loadProjectList() {
		loadProjectsJob?.cancel()
		loadProjectsJob = scope.launch {
			val projects = projectsRepository.getProjects(state.value.projectsDir)
			val projectData = projects.mapNotNull {  projectDef ->
				val metadata = projectsRepository.loadMetadata(projectDef)
				if(metadata != null) {
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
			Napier.i("Project created: $projectName")
			loadProjectList()
		} else {
			Napier.e("Failed to create Project: $projectName")
		}
	}

	override fun deleteProject(projectDef: ProjectDef) {
		if (projectsRepository.deleteProject(projectDef)) {
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
}