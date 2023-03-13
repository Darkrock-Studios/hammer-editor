package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
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
	fun beginSetupServer()
	fun cancelServerSetup()
	suspend fun setupServer(url: String, email: String, password: String, create: Boolean): Boolean
    suspend fun authTest()

	data class State(
        val projectsDir: HPath,
        val projects: List<ProjectData> = mutableListOf(),
        val location: Locations = Locations.Projects,
        val uiTheme: UiTheme,
        val serverSetup: Boolean = false,
        val serverUrl: String? = null,
	)

	enum class Locations(val text: String) {
		Projects("Projects"),
		Sittings("Settings")
	}
}