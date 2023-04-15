package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ProjectsList : HammerComponent {
	val state: Value<State>

	fun loadProjectList()
	fun selectProject(projectDef: ProjectDef)
	fun createProject(projectName: String)
	fun deleteProject(projectDef: ProjectDef)
	fun syncProjects(callback: (Boolean) -> Unit)
	fun showProjectsSync()
	fun hideProjectsSync()
	fun cancelProjectsSync()
	suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata?

	data class State(
		val projects: List<ProjectData> = mutableListOf(),
		val projectsPath: HPath,
		val isServerSynced: Boolean = false,
		val syncState: SycState = SycState(),
	)

	data class SycState(
		val showProjectSync: Boolean = false,
		val syncComplete: Boolean = false,
		val syncLog: List<String> = emptyList()
	)
}