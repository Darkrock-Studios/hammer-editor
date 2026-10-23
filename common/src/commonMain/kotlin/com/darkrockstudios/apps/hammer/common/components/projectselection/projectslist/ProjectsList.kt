package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface ProjectsList : HammerComponent, ComponentToaster {
	val state: Value<State>
	val modalRouterState: Value<ChildSlot<ProjectListModalRouter.Config, ModalDestination>>

	fun loadProjectList()
	fun selectProject(projectDef: ProjectDef)
	/** Opens the create dialog for a project in [parent], or the top level when null. */
	fun showCreate(parent: GroupDef? = null)
	fun hideCreate()
	fun createProject(projectName: String, parent: GroupDef? = null)
	fun beginProjectImport()
	fun cancelImportFilePicker()
	fun selectImportFile(name: String, content: ByteArray)
	fun updateImportProjectName(name: String)
	fun updateImportOptions(options: ImportOptions)
	fun cancelImportDialog()
	suspend fun confirmImportDialog()
	fun deleteProject(projectDef: ProjectDef)
	fun renameProject(projectDef: ProjectDef, newName: String)
	fun syncProjects(callback: (Boolean) -> Unit)
	fun showProjectsSync()
	fun hideProjectsSync()
	fun cancelProjectsSync()

	/** Resolves the pending story-idea sync conflict; null leaves the idea unsynced this session. */
	fun resolveIdeaConflict(resolution: StoryIdea?)
	suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata?
	fun onProjectNameUpdate(newProjectName: String)
	fun showProjectRename(projectDef: ProjectDef)
	fun dismissProjectRename()
	fun showProjectDelete(projectDef: ProjectDef)
	fun dismissProjectDelete()

	/** Moves the project into [target], or the top level when null. */
	fun moveProject(projectDef: ProjectDef, target: GroupDef?)
	fun createGroup(name: String, parent: GroupDef?, description: String? = null)
	fun renameGroup(groupDef: GroupDef, newName: String)
	fun moveGroup(groupDef: GroupDef, newParent: GroupDef?)

	/** Deletes the group only: everything in it moves up one level. */
	fun deleteGroup(groupDef: GroupDef)
	fun toggleGroupCollapsed(groupDef: GroupDef)
	fun updateGroupDescription(groupDef: GroupDef, description: String?)
	fun showGroupCreate(parent: GroupDef?)
	fun showGroupRename(groupDef: GroupDef)
	fun showGroupDelete(groupDef: GroupDef)
	fun showGroupDescription(groupDef: GroupDef)
	fun showMoveToGroup(target: MoveTarget)
	fun dismissGroupModal()

	@Serializable
	data class State(
		val projects: List<ProjectData> = mutableListOf(),
		/** Parents first. */
		val groups: List<GroupData> = emptyList(),
		/** The group holding each project, null at the top level. */
		val placement: Map<ProjectId, GroupId?> = emptyMap(),
		val projectsPath: HPath,
		val isServerSynced: Boolean = false,
		// In-flight sync progress is not meaningful after a process death; don't restore it.
		@Transient val syncState: SyncState = SyncState(),
		val createDialogProjectName: String = "",
		val showImportFilePicker: Boolean = false,
		val showImportDialog: Boolean = false,
		val importOptions: ImportOptions = ImportOptions(),
		val importSourceName: String = "",
		val importProjectName: String = "",
		val importFileContent: ByteArray = ByteArray(0),
		val importPreview: ImportPreview = ImportPreview(emptyList()),
		// A parse in flight is meaningless after a process death; don't restore it.
		@Transient val isParsingImport: Boolean = false,
	)

	@Serializable
	data class SyncState(
		val syncComplete: Boolean = false,
		val syncLog: List<SyncLogMessage> = emptyList(),
		val projectsStatus: Map<String, ProjectSyncStatus> = emptyMap(),
		// A mid-sync question for the user, meaningless after process death.
		@Transient val ideaConflict: IdeaConflict? = null,
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
		NeedsResolution,
		Complete,
		Canceled
	}

	sealed class ModalDestination {
		data object None : ModalDestination()
		data object ProjectSync : ModalDestination()
		data class ProjectRename(val projectDef: ProjectDef) : ModalDestination()
		data class ProjectCreate(val parent: GroupDef? = null) : ModalDestination()
		data class ProjectDelete(val projectDef: ProjectDef) : ModalDestination()
		data class GroupCreate(val parent: GroupDef?) : ModalDestination()
		data class GroupRename(val groupDef: GroupDef) : ModalDestination()
		data class GroupDelete(val groupDef: GroupDef) : ModalDestination()
		data class GroupDescription(val groupDef: GroupDef) : ModalDestination()
		data class MoveToGroup(val target: MoveTarget) : ModalDestination()
		data class ServerReauth(val component: ServerReauthentication) : ModalDestination()
		data class ProtocolMismatch(val component: com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatch) :
			ModalDestination()
	}
}

/**
 * True only while a sync is still working. Closing the dialog mid-sync should confirm
 * cancellation; once every project is in a terminal state (Complete/Failed/NeedsResolution/Canceled)
 * the sync is no longer active, so closing should just close — even if [syncComplete] never flipped.
 */
val ProjectsList.SyncState.hasActiveSync: Boolean
	get() = !syncComplete && projectsStatus.values.any { status ->
		status.status == ProjectsList.Status.Pending || status.status == ProjectsList.Status.Syncing
	}
