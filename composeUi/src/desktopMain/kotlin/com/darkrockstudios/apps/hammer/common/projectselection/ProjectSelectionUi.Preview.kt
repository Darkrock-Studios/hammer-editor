package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectMetadata


@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Preview
@Composable
private fun ProjectSelectionUiPreview() {
	ProjectSelectionUi(fakeProjectSelectionComponent())
}

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
                    uiTheme = UiTheme.Dark
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

		override suspend fun authTest() {}
		override fun removeServer() {}
		override fun syncProjects(callback: (Boolean) -> Unit) {}
		override fun showProjectsSync() {}
		override fun hideProjectsSync() {}
	}
}

fun fakeProjectData() = ProjectData(
	fakeProjectDef(),
	fakeProjectMetadata()
)