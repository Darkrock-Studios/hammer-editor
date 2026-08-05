package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectListModalRouter
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
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

fun previewProject(name: String, tags: Set<String> = setOf("fantasy", "draft")) = ProjectData(
	definition = ProjectDef(name = name, path = HPath(name = name, path = "/$name", isAbsolute = true)),
	metadata = fakeProjectMetadata(),
	storedData = StoredData(authorName = "A. Writer", tags = tags),
)

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
) = object : ProjectsList {
	override val state: Value<ProjectsList.State> = MutableValue(
		ProjectsList.State(
			projects = projects,
			projectsPath = fakeProjectDef().path,
			isServerSynced = isServerSynced,
		)
	)
	override val modalRouterState: Value<ChildSlot<ProjectListModalRouter.Config, ProjectsList.ModalDestination>> =
		MutableValue(ChildSlot(child = null))
	override val toast: Flow<ToastMessage> = MutableSharedFlow()

	override fun loadProjectList() {}
	override fun selectProject(projectDef: ProjectDef) {}
	override fun showCreate() {}
	override fun hideCreate() {}
	override fun createProject(projectName: String) {}
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

	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: String) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: String) {}
	override suspend fun showToast(message: Msg) {}
}
