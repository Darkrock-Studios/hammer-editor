package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSyncComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

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
			childFactory = ::createChild
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is ProjectRoot.ModalDestination.None
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectRoot.ModalDestination =
		when (config) {
			Config.None -> ProjectRoot.ModalDestination.None
			Config.ProjectSync -> ProjectRoot.ModalDestination.ProjectSync(
				ProjectSyncComponent(componentContext, projectDef, ::dismissProjectSync)
			)
		}

	fun showProjectSync() {
		navigation.activate(Config.ProjectSync)
	}

	fun dismissProjectSync() {
		navigation.activate(Config.None)
	}

	sealed class Config : Parcelable {
		@Parcelize
		object None : Config()

		@Parcelize
		object ProjectSync : Config()
	}
}