package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata

interface ProjectSelection : HammerComponent {
    val showProjectDirectory: Boolean
    val state: Value<State>

    fun loadProjectList()
    fun setProjectsDir(path: String)
    fun selectProject(projectDef: ProjectDef)
    fun createProject(projectName: String)
    fun deleteProject(projectDef: ProjectDef)
    fun showLocation(location: Locations)
    fun setUiTheme(theme: UiTheme)
    suspend fun reinstallExampleProject()
    suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata?

    data class State(
        val projectsDir: HPath,
        val projects: List<ProjectData> = mutableListOf(),
        val location: Locations = Locations.Projects,
        val uiTheme: UiTheme
    )

    enum class Locations(val text: String) {
        Projects("Projects"),
        Sittings("Settings")
    }
}