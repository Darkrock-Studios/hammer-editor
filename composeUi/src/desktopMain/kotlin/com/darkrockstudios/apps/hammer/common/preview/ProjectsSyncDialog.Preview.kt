package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.Padded
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsync.*
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectStatusUi
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSyncDialogContents
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogContents
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Preview
@Composable
private fun ProjectsSyncDialogPreview() = Padded {
	Box(modifier = Modifier.border(1.dp, Color.Black).padding(16.dp)) {
		ProjectsSyncDialogContents(
			component = fakeProjectsList
		)
	}
}

@Preview
@Composable
private fun ProjectsSyncLogPreview() = Padded {
	Box(modifier = Modifier.border(1.dp, Color.Black).padding(16.dp)) {
		SyncLogContents(component = fakeProjectsList)
	}
}

@Preview
@Composable
private fun ProjectStatusUiPreview() = Padded {
	val notStarted = ProjectsList.ProjectSyncStatus("Test Project 1")
	ProjectStatusUi(notStarted)
}

private val fakeProjectsList = object : ProjectsList {
	override val state: Value<ProjectsList.State> = MutableValue(
		ProjectsList.State(
			projectsPath = HPath("", "", true), syncState = ProjectsList.SyncState(
				showProjectSync = true, syncComplete = false, projectsStatus = mapOf(
					"Test Project 1" to ProjectsList.ProjectSyncStatus("Test Project 1"),
					"Test Project 2" to ProjectsList.ProjectSyncStatus(
						"Test Project 2", 0.25f, ProjectsList.Status.Syncing
					),
					"Test Project 3" to ProjectsList.ProjectSyncStatus(
						"Test Project 3", 0.5f, ProjectsList.Status.Failed
					),
					"Test Project 4" to ProjectsList.ProjectSyncStatus(
						"Test Project 4", 1f, ProjectsList.Status.Complete
					),

					), syncLog = listOf(
					syncLogI("Log 1 asd qwe zxc dasd qwe", "Project1"),
					syncLogW("Log 2 asd qwe zxc dasd qwe", "Project1"),
					syncLogE("Log 3 asd qwe zxc dasd qwe", "Project1"),
					syncLogD("Log 4 asd qwe zxc dasd qwe", "Project1"),
					syncAccLogD("Log 4 asd qwe zxc dasd qwe"),
				)
			)
		)
	)
	override val toast: Flow<ToastMessage> = MutableSharedFlow()
	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: Msg) {}
	override fun loadProjectList() {}
	override fun selectProject(projectDef: ProjectDef) {}
	override fun showCreate() {}
	override fun hideCreate() {}
	override fun createProject(projectName: String) {}
	override fun deleteProject(projectDef: ProjectDef) {}
	override fun syncProjects(callback: (Boolean) -> Unit) {}
	override fun showProjectsSync() {}
	override fun hideProjectsSync() {}
	override fun cancelProjectsSync() {}
	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? = null
	override fun onProjectNameUpdate(newProjectName: String) {}
}