package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectListModalRouter
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectMetadata
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.StringResource
import kotlin.uuid.Uuid
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData as StoredData

@Preview
@Composable
fun ScreenProjectListUiPreview() {
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		ProjectListUi(previewComponent, rootSnackbar)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenProjectListUiTabletPreview() {
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		TabletPreviewSurface {
			ProjectListUi(previewComponent, rootSnackbar)
		}
	}
}

@Preview
@Composable
fun ScreenProjectListUiGroupedPreview() {
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		ProjectListUi(groupedPreviewComponent, rootSnackbar)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenProjectListUiGroupedTabletPreview() {
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		TabletPreviewSurface {
			ProjectListUi(groupedPreviewComponent, rootSnackbar)
		}
	}
}

fun previewProject(
	name: String,
	tags: Set<String> = setOf("fantasy", "draft"),
	parent: GroupDef? = null,
) = ProjectData(
	definition = ProjectDef(
		id = ProjectId.fromUUID(Uuid.fromLongs(0L, name.hashCode().toLong())),
		name = name,
		path = HPath(name = name, path = "${parent?.path?.path ?: ""}/$name", isAbsolute = true),
	),
	metadata = fakeProjectMetadata(),
	storedData = StoredData(authorName = "A. Writer", tags = tags),
)

fun previewGroup(name: String, parent: GroupDef? = null, description: String? = null) = GroupDef(
	id = GroupId.fromUUID(Uuid.fromLongs(1L, name.hashCode().toLong())),
	name = name,
	path = HPath(name = name, path = "${parent?.path?.path ?: ""}/$name", isAbsolute = true),
	parentId = parent?.id,
	description = description,
)

private val groupedPreviewComponent: ProjectsList by lazy {
	val trilogy = previewGroup("The Salt Trilogy", description = "Three books about the drowned coast")
	val drafts = previewGroup("Abandoned Drafts", parent = trilogy)
	val coursework = previewGroup("Workshop Pieces")
	val inTrilogy = listOf(
		previewProject("Salt & Ash", parent = trilogy),
		previewProject("Brine", parent = trilogy),
	)
	val inDrafts = listOf(previewProject("Undertow", tags = emptySet(), parent = drafts))
	val inCoursework = listOf(previewProject("Short Fiction I", tags = emptySet(), parent = coursework))
	val topLevel = listOf(previewProject("The Lighthouse"), previewProject("Wonderland"))
	fakeProjectsList(
		projects = inTrilogy + inDrafts + inCoursework + topLevel,
		isServerSynced = true,
		groups = listOf(
			GroupData(trilogy),
			GroupData(coursework, collapsed = true),
			GroupData(drafts),
		),
		placement = inTrilogy.associate { it.definition.id to trilogy.id } +
			inDrafts.associate { it.definition.id to drafts.id } +
			inCoursework.associate { it.definition.id to coursework.id } +
			topLevel.associate { it.definition.id to null },
	)
}

private val previewComponent = fakeProjectsList(
	listOf(
		previewProject("The Lighthouse"),
		previewProject("Wonderland"),
		previewProject("Salt & Ash"),
	),
	isServerSynced = true,
)

/** Shared by the previews above and the composeUi footer tests. */
fun fakeProjectsList(
	projects: List<ProjectData>,
	isServerSynced: Boolean = false,
	groups: List<GroupData> = emptyList(),
	placement: Map<ProjectId, GroupId?> = emptyMap(),
) = object : ProjectsList {
	override val state: Value<ProjectsList.State> = MutableValue(
		ProjectsList.State(
			projects = projects,
			groups = groups,
			placement = placement,
			projectsPath = fakeProjectDef().path,
			isServerSynced = isServerSynced,
		)
	)
	override val modalRouterState: Value<ChildSlot<ProjectListModalRouter.Config, ProjectsList.ModalDestination>> =
		MutableValue(ChildSlot(child = null))
	override val toast: Flow<ToastMessage> = MutableSharedFlow()

	override fun loadProjectList() {}
	override fun selectProject(projectDef: ProjectDef) {}
	override fun showCreate(parent: GroupDef?) {}
	override fun hideCreate() {}
	override fun createProject(projectName: String, parent: GroupDef?) {}
	override fun beginProjectImport() {}
	override fun cancelImportFilePicker() {}
	override fun selectImportFile(name: String, content: ByteArray) {}
	override fun updateImportProjectName(name: String) {}
	override fun updateImportOptions(options: ImportOptions) {}
	override fun cancelImportDialog() {}
	override suspend fun confirmImportDialog() {}
	override fun deleteProject(projectDef: ProjectDef) {}
	override fun renameProject(projectDef: ProjectDef, newName: String) {}
	override fun syncProjects(callback: (Boolean) -> Unit) {}
	override fun showProjectsSync() {}
	override fun hideProjectsSync() {}
	override fun cancelProjectsSync() {}
	override fun resolveIdeaConflict(resolution: StoryIdea?) {}
	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? = null
	override fun onProjectNameUpdate(newProjectName: String) {}
	override fun showProjectRename(projectDef: ProjectDef) {}
	override fun dismissProjectRename() {}
	override fun showProjectDelete(projectDef: ProjectDef) {}
	override fun dismissProjectDelete() {}
	override fun moveProject(projectDef: ProjectDef, target: GroupDef?) {}
	override fun createGroup(name: String, parent: GroupDef?, description: String?) {}
	override fun renameGroup(groupDef: GroupDef, newName: String) {}
	override fun moveGroup(groupDef: GroupDef, newParent: GroupDef?) {}
	override fun deleteGroup(groupDef: GroupDef) {}
	override fun toggleGroupCollapsed(groupDef: GroupDef) {}
	override fun updateGroupDescription(groupDef: GroupDef, description: String?) {}
	override fun showGroupCreate(parent: GroupDef?) {}
	override fun showGroupRename(groupDef: GroupDef) {}
	override fun showGroupDelete(groupDef: GroupDef) {}
	override fun showGroupDescription(groupDef: GroupDef) {}
	override fun showMoveToGroup(target: MoveTarget) {}
	override fun dismissGroupModal() {}

	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: String) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: String) {}
	override suspend fun showToast(message: Msg) {}
}
