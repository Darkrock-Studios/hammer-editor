package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.core.component.inject

class ProjectSelectionComponent(
    componentContext: ComponentContext,
    override val showProjectDirectory: Boolean = false,
    private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {
    private val globalSettingsRepository by inject<GlobalSettingsRepository>()
    private val projectsRepository by inject<ProjectsRepository>()
    private val projectRepository by inject<ProjectRepository>()

    private val _value = MutableValue(
        ProjectSelection.State(
            projectsDir = projectsRepository.getProjectsDirectory(),
            projectDefs = emptyList()
        )
    )
    override val state: Value<ProjectSelection.State> = _value

    init {
        scope.launch {
            globalSettingsRepository.globalSettingsUpdates.collect { settings ->
                _value.reduce {
                    val projectsPath = settings.projectsDirectory.toPath().toHPath()
                    it.copy(projectsDir = projectsPath)
                }
            }
        }
    }

    override fun loadProjectList() {
        _value.reduce {
            val projects = projectsRepository.getProjects(state.value.projectsDir)
            it.copy(projectDefs = projects)
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
        _value.reduce {
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
        projectRepository.closeEditor(projectDef)
        if (projectsRepository.deleteProject(projectDef)) {
            loadProjectList()
        }
    }

    override fun onStart() {
        projectRepository.closeEditors()
    }
}