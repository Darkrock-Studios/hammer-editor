package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import org.koin.core.component.inject

class ProjectSelectionComponent(
    componentContext: ComponentContext,
    private val onProjectSelected: (project: Project) -> Unit
) : ProjectSelection, ComponentContext by componentContext, Lifecycle.Callbacks {

    private val projectsRepository by inject<ProjectsRepository>()
    private val projectRepository by inject<ProjectRepository>()

    private val _value = MutableValue(
        ProjectSelection.State(
            projectsDir = projectsRepository.getProjectsDirectory(),
            projects = emptyList()
        )
    )
    override val state: Value<ProjectSelection.State> = _value

    init {
        loadProjectList()
        lifecycle.subscribe(this)
    }

    override fun loadProjectList() {
        _value.reduce {
            val projects = projectsRepository.getProjects(state.value.projectsDir)
            it.copy(projects = projects)
        }
    }

    override fun setProjectsDir(path: HPath) {
        _value.reduce {
            it.copy(projectsDir = path)
        }
    }

    override fun selectProject(project: Project) = onProjectSelected(project)

    override fun createProject(projectName: String) {
        if (projectsRepository.createProject(projectName)) {
            Napier.i("Project created: $projectName")
            loadProjectList()
        } else {
            Napier.e("Failed to create Project: $projectName")
        }
    }

    override fun onStart() {
        projectRepository.closeEditors()
    }
}