package com.darkrockstudios.apps.hammer.android.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.Child.Created
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackHandler
import com.darkrockstudios.apps.hammer.android.ProjectSelectContent
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection.Config
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection.Destination
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectListModalRouter
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.Msg
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
	override val modalRouterState: Value<ChildSlot<ProjectListModalRouter.Config, ProjectsList.ModalDestination>>
		get() = MutableValue(ChildSlot(null))

	override fun loadProjectList() {}
	override fun selectProject(projectDef: ProjectDef) {}
	override fun showCreate() {}
	override fun hideCreate() {}
	override fun createProject(projectName: String) {}
	override fun deleteProject(projectDef: ProjectDef) {}
	override fun renameProject(projectDef: ProjectDef, newName: String) {}
	override fun syncProjects(callback: (Boolean) -> Unit) {}
	override fun showProjectsSync() {}
	override fun hideProjectsSync() {}
	override fun cancelProjectsSync() {}
	override suspend fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata? = null
	override fun onProjectNameUpdate(newProjectName: String) {}
	override fun showProjectRename(projectDef: ProjectDef) {}
	override fun dismissProjectRename() {}
	override val toast = MutableSharedFlow<ToastMessage>()
	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: Msg) {}
}

val dummyBackHandler = object : BackHandler {
	override fun isRegistered(callback: BackCallback) = false
	override fun register(callback: BackCallback) {}
	override fun unregister(callback: BackCallback) {}
}

val component = object : ProjectSelection {
	override val stack: Value<ChildStack<Config, Destination>> =
		MutableValue<ChildStack<Config, Destination>>(
			ChildStack(
				Created(
					configuration = Config.ProjectsList,
					instance = Destination.ProjectsListDestination(projectListComponent)
				)
			)
		)

	override fun isAtRoot() = false
	override fun onBack() {}
	override fun showLocation(location: ProjectSelection.Locations) {}
	override val backHandler = dummyBackHandler
}

@Preview
@Composable
private fun ProjectSelectActivityPreview() {
	ProjectSelectContent(component)
}
