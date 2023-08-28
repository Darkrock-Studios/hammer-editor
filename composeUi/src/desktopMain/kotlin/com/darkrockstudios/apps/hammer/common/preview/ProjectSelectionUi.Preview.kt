package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCard

/*
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Preview
@Composable
private fun ProjectSelectionUiPreview() {
	ProjectSelectionUi(fakeProjectSelectionComponent())
}
*/

@Preview
@Composable
private fun ProjectCardPreview() {
	val data = fakeProjectData()
	Column {
		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(false) {
			ProjectCard(data, {}, {})
		}

		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(true) {
			ProjectCard(data, {}, {})
		}
	}
}

/*
private fun fakeProjectSelectionComponent(): ProjectSelection {
	return object : ProjectSelection {
		override val showProjectDirectory = true
		override val state: Value<ProjectSelection.State>
			get() = MutableValue(
				ProjectSelection.State(
					projectsDir = HPath("/asd/asd", "asd", true),
					projects = listOf(
						fakeProjectData(),
						fakeProjectData(),
						fakeProjectData(),
						fakeProjectData(),
					),
					uiTheme = UiTheme.Dark,
					syncAutomaticSync = true,
					syncAutomaticBackups = true,
					syncAutoCloseDialog = true,
					maxBackups = 5
				)
			)

		override fun loadProjectList() {}
		override fun setProjectsDir(path: String) {}
		override fun selectProject(projectDef: ProjectDef) {}
		override fun createProject(projectName: String) {}
		override fun deleteProject(projectDef: ProjectDef) {}
		override fun showLocation(location: ProjectSelection.Locations) {}
		override fun setUiTheme(theme: UiTheme) {}
		override suspend fun reinstallExampleProject() {}
		override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? {
			return null
		}

		override fun beginSetupServer() {}
		override fun cancelServerSetup() {}
		override suspend fun setupServer(
			ssl: Boolean,
			url: String,
			email: String,
			password: String,
			create: Boolean
		): Result<Boolean> = Result.success(true)

		override suspend fun authTest() = true
		override fun removeServer() {}
		override fun syncProjects(callback: (Boolean) -> Unit) {}
		override fun showProjectsSync() {}
		override fun hideProjectsSync() {}
		override fun cancelProjectsSync() {}
		override suspend fun setAutomaticBackups(value: Boolean) {}
		override suspend fun setAutoCloseDialogs(value: Boolean) {}
		override suspend fun setAutoSyncing(value: Boolean) {}
		override suspend fun setMaxBackups(value: Int) {}
	}
}
*/

fun fakeProjectData() = ProjectData(
	fakeProjectDef(),
	fakeProjectMetadata()
)