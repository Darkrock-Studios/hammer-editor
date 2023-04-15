package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath

@Preview
@Composable
private fun ProjectsSyncDialogPreview() {
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

	Box(
		modifier = Modifier.padding(32.dp).border(1.dp, Color.Black),
	) {
		Box(modifier = Modifier.padding(16.dp)) {
			ProjectsSyncDialogContents(
				component = fakeProjectsList, scope = scope, snackbarHostState = snackbarHostState
			)
		}
	}
}

@Preview
@Composable
private fun ProjectStatusUiPreview() {
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
					"Log 1 asd qwe zxc dasd qwe",
					"Log 2 asd qwe zxc dasd qwe",
					"Log 3 asd qwe zxc dasd qwe",
					"Log 4 asd qwe zxc dasd qwe",
				)
			)
		)
	)

	override fun loadProjectList() {}
	override fun selectProject(projectDef: ProjectDef) {}
	override fun createProject(projectName: String) {}
	override fun deleteProject(projectDef: ProjectDef) {}
	override fun syncProjects(callback: (Boolean) -> Unit) {}
	override fun showProjectsSync() {}
	override fun hideProjectsSync() {}
	override fun cancelProjectsSync() {}
	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? = null
}