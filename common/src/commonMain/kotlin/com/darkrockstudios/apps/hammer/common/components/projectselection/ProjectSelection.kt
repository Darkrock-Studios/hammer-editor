package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

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
	suspend fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean
	): Result<Boolean>

	suspend fun authTest()
	fun removeServer()
	fun syncProjects(callback: (Boolean) -> Unit)
	fun showProjectsSync()
	fun hideProjectsSync()

	data class State(
		val projectsDir: HPath,
		val projects: List<ProjectData> = mutableListOf(),
		val location: Locations = Locations.Projects,
		val uiTheme: UiTheme,
		val serverSetup: Boolean = false,
		val serverUrl: String? = null,
		val serverError: String? = null,
		val syncState: SycState = SycState()
	)

	data class SycState(
		val showProjectSync: Boolean = false,
		val syncComplete: Boolean = false,
		val syncLog: List<String> = emptyList()
	)

	enum class Locations(val text: String) {
		Projects("Projects"),
		Sittings("Settings")
	}
}