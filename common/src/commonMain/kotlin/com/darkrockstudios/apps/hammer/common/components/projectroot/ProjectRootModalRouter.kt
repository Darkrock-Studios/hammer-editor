package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot.ModalDestination.*
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronizationComponent
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthenticationComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

class ProjectRootModalRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
) : Router {
	private val navigation = SlotNavigation<Config>()

	val state: Value<ChildSlot<Config, ProjectRoot.ModalDestination>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "ProjectRootModalRouter",
			childFactory = ::createChild,
			serializer = Config.serializer(),
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is None
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectRoot.ModalDestination =
		when (config) {
			Config.None -> None
			Config.ProjectSync -> ProjectSync(
				ProjectSynchronizationComponent(
					componentContext,
					projectDef,
					::dismissProjectSync,
					::showReauthorizeDialog
				)
			)

			Config.ServerReauth -> ServerReauth(
				ServerReauthenticationComponent(
					componentContext,
					projectDef,
					::dismissProjectSync,
					::showProjectSync,
				)
			)
		}

	fun showProjectSync() {
		navigation.activate(Config.ProjectSync)
	}

	fun dismissProjectSync() {
		navigation.activate(Config.None)
	}

	fun showReauthorizeDialog() {
		navigation.activate(Config.ServerReauth)
	}

	fun dismissReauthorizeDialog() {
		navigation.activate(Config.None)
	}

	@Serializable
	sealed class Config {
		@Serializable
		data object None : Config()

		@Serializable
		data object ProjectSync : Config()

		@Serializable
		data object ServerReauth : Config()
	}
}