package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata


@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Preview
@Composable
private fun ProjectSelectionUiPreview() {
	ProjectSelectionUi(fakeProjectSelectionComponent())
}

@Preview
@Composable
private fun ProjectCardPreview() {
	val component = fakeProjectSelectionComponent()
	val scope = rememberCoroutineScope()

	Column {
		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(false) {
			ProjectCard(component, fakeProjectDef(), scope, {}, {})
		}

		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(true) {
			ProjectCard(component, fakeProjectDef(), scope, {}, {})
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
					projectDefs = listOf(
						fakeProjectDef(),
						fakeProjectDef(),
						fakeProjectDef(),
						fakeProjectDef(),
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
	}
}