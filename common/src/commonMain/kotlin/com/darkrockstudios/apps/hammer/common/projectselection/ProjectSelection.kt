package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.Project

interface ProjectSelection {
    val state: Value<State>

    fun loadProjectList()
    fun setProjectsDir(path: String)
    fun selectProject(project: Project)

    data class State(
        val projectsDir: String = "",
        val projects: List<Project> = mutableListOf()
    )
}