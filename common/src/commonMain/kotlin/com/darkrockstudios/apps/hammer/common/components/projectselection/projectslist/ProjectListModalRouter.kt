package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthenticationComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

class ProjectListModalRouter(
	componentContext: ComponentContext,
) : Router {
	private val navigation = SlotNavigation<Config>()

	val state: Value<ChildSlot<Config, ProjectsList.ModalDestination>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "ProjectListModalRouter",
			childFactory = ::createChild,
			serializer = Config.serializer(),
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is ProjectsList.ModalDestination.None
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectsList.ModalDestination =
		when (config) {
			Config.None -> ProjectsList.ModalDestination.None
			is Config.ProjectSync -> ProjectsList.ModalDestination.ProjectSync
			is Config.ProjectRename -> ProjectsList.ModalDestination.ProjectRename(config.projectDef)
			is Config.ProjectCreate -> ProjectsList.ModalDestination.ProjectCreate
			is Config.ProjectDelete -> ProjectsList.ModalDestination.ProjectDelete(config.projectDef)
			is Config.ServerReauth -> ProjectsList.ModalDestination.ServerReauth(
				ServerReauthenticationComponent(
					componentContext = componentContext,
					dismissAuth = ::dismissServerReauthentication,
					onReauthSuccess = ::showProjectSync,
				)
			)
		}

	fun showProjectSync() {
		navigation.activate(Config.ProjectSync)
	}

	fun dismissProjectSync() {
		navigation.activate(Config.None)
	}

	fun showProjectRename(projectDef: ProjectDef) {
		navigation.activate(Config.ProjectRename(projectDef))
	}

	fun dismissProjectRename() {
		navigation.activate(Config.None)
	}

	fun showProjectCreate() {
		navigation.activate(Config.ProjectCreate)
	}

	fun dismissProjectCreate() {
		navigation.activate(Config.None)
	}

	fun showProjectDelete(projectDef: ProjectDef) {
		navigation.activate(Config.ProjectDelete(projectDef))
	}

	fun dismissProjectDelete() {
		navigation.activate(Config.None)
	}

	fun showServerReauthentication() {
		navigation.activate(Config.ServerReauth)
	}

	fun dismissServerReauthentication() {
		navigation.activate(Config.None)
	}

	@Serializable
	sealed class Config {
		@Serializable
		data object None : Config()

		@Serializable
		data object ProjectSync : Config()

		@Serializable
		data class ProjectRename(val projectDef: ProjectDef) : Config()

		@Serializable
		data object ProjectCreate : Config()

		@Serializable
		data class ProjectDelete(val projectDef: ProjectDef) : Config()

		@Serializable
		data object ServerReauth : Config()
	}
}
