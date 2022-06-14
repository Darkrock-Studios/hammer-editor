package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.di.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ProjectSelection : HammerComponent {
    val state: Value<State>

    fun loadProjectList()
    fun setProjectsDir(path: HPath)
    fun selectProject(project: Project)
    fun createProject(projectName: String)
    fun deleteProject(project: Project)

    data class State(
        val projectsDir: HPath,
        val projects: List<Project> = mutableListOf()
    )
}