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
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
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

    private val accountRepository: AccountRepository by inject()
    private val globalSettingsRepository: GlobalSettingsRepository by inject()
    private val projectsRepository: ProjectsRepository by inject()
    private val exampleProjectRepository: ExampleProjectRepository by inject()
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