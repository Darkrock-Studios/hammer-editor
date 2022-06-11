package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.getProjectsForDirectory

class ProjectSelectionComponent(
    componentContext: ComponentContext,
    private val onProjectSelected: (project: Project) -> Unit
): ProjectSelection, ComponentContext by componentContext {

    private val _value = MutableValue(ProjectSelection.State(projectsDir = ""))
    override val state: Value<ProjectSelection.State> = _value

    override fun loadProjectList() {
        _value.reduce {
            val projects = getProjectsForDirectory(state.value.projectsDir)
            _value.value.copy(projects = projects)
        }
    }

    override fun setProjectsDir(path: String) {
        _value.reduce {
            _value.value.copy(projectsDir = path)
        }
    }

    override fun selectProject(project: Project) = onProjectSelected(project)
}