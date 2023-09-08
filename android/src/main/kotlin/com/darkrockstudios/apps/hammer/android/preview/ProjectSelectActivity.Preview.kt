package com.darkrockstudios.apps.hammer.android.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.Child.Created
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.android.ProjectSelectContent
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow


val projectListComponent = object : ProjectsList {
	override val state: Value<ProjectsList.State> =
		MutableValue(
			ProjectsList.State(
				projectsPath = HPath("/", "root", true)
			)
		)

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
	override val toast = MutableSharedFlow<ToastMessage>()
	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
}

val component = object : ProjectSelection {
	override val slot: Value<ChildSlot<ProjectSelection.Config, ProjectSelection.Destination>> =
		MutableValue<ChildSlot<ProjectSelection.Config, ProjectSelection.Destination>>(
			ChildSlot(
				Created(
					configuration = ProjectSelection.Config.ProjectsList,
					instance = ProjectSelection.Destination.ProjectsListDestination(projectListComponent)
				)
			)
		)
	override val showProjectDirectory = true
	override fun showLocation(location: ProjectSelection.Locations) {}
}

@Preview
@Composable
private fun ProjectSelectActivityPreview() {
	ProjectSelectContent(component)
}