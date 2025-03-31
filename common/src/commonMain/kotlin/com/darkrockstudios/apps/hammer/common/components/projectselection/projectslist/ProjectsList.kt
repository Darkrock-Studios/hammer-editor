package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

interface ProjectsList : HammerComponent, ComponentToaster {
	val state: Value<State>
	val modalRouterState: Value<ChildSlot<ProjectListModalRouter.Config, ModalDestination>>

	fun loadProjectList()
	fun selectProject(projectDef: ProjectDef)
	fun showCreate()
	fun hideCreate()
	fun createProject(projectName: String)
	fun deleteProject(projectDef: ProjectDef)
	fun renameProject(projectDef: ProjectDef, newName: String)
	fun syncProjects(callback: (Boolean) -> Unit)
	fun showProjectsSync()
	fun hideProjectsSync()
	fun cancelProjectsSync()
	suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata?
	fun onProjectNameUpdate(newProjectName: String)
	fun showProjectRename(projectDef: ProjectDef)
	fun dismissProjectRename()

	@Serializable
	data class State(
		val projects: List<ProjectData> = mutableListOf(),
		val projectsPath: HPath,
		val isServerSynced: Boolean = false,
		val syncState: SyncState = SyncState(),
		val createDialogProjectName: String = "",
	)

	@Serializable
	data class SyncState(
		val syncComplete: Boolean = false,
		val syncLog: List<SyncLogMessage> = emptyList(),
		val projectsStatus: Map<String, ProjectSyncStatus> = emptyMap()
	)

	@Serializable
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

	sealed class ModalDestination {
		data object None : ModalDestination()
		data object ProjectSync : ModalDestination()
		data class ProjectRename(val projectDef: ProjectDef) : ModalDestination()
		data object ProjectCreate : ModalDestination()
		data class ServerReauth(val component: ServerReauthentication) : ModalDestination()
	}
}
