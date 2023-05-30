package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsync.SyncLogMessage
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
		val syncState: SyncState = SyncState(),
		val toast: String? = null
	)

	data class SyncState(
		val showProjectSync: Boolean = false,
		val syncComplete: Boolean = false,
		val syncLog: List<SyncLogMessage> = emptyList(),
		val projectsStatus: Map<String, ProjectSyncStatus> = emptyMap()
	)

	data class ProjectSyncStatus(
		val projectName: String,
		val progress: Float = 0f,
		val status: Status = Status.Pending
	)

	enum class Status {
		Pending,
		Syncing,
		Failed,
		Complete,
		Canceled
	}
}