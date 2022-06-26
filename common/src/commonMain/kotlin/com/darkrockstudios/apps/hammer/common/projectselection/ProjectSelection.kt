package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.di.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ProjectSelection : HammerComponent {
    val state: Value<State>

    fun loadProjectList()
    fun setProjectsDir(path: HPath)
    fun selectProject(projectDef: ProjectDef)
    fun createProject(projectName: String)
    fun deleteProject(projectDef: ProjectDef)

    data class State(
        val projectsDir: HPath,
        val projectDefs: List<ProjectDef> = mutableListOf()
    )
}