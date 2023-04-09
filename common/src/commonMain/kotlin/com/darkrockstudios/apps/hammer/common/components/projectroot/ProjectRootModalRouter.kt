package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.overlay.ChildOverlay
import com.arkivanov.decompose.router.overlay.OverlayNavigation
import com.arkivanov.decompose.router.overlay.childOverlay
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSyncComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class ProjectRootModalRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
) : Router {
	private val navigation = OverlayNavigation<Config>()

	val state: Value<ChildOverlay<Config, ProjectRoot.ModalDestination>> =
		componentContext.childOverlay(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "ProjectRootModalRouter",
			childFactory = ::createChild
		)

	override fun isAtRoot(): Boolean {
		return state.value.overlay?.instance is ProjectRoot.ModalDestination.None
	}

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
		navigation.navigate(
			transformer = { configuration ->
				if (configuration is Config.ProjectSync)
					throw IllegalStateException("Can't navigate to Project sync, already there")
				Config.ProjectSync
			},
			onComplete = { _, _ -> }
		)
	}

	fun dismissProjectSync() {
		navigation.navigate(
			transformer = { configuration ->
				if (configuration is Config.None)
					throw IllegalStateException("Can't dismiss Project sync, it isnt shown")
				Config.None
			},
			onComplete = { _, _ -> }
		)
	}

	sealed class Config : Parcelable {
		@Parcelize
		object None : Config()

		@Parcelize
		object ProjectSync : Config()
	}
}