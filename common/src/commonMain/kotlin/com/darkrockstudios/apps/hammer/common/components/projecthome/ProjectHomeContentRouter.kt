package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

class ProjectHomeContentRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
) : Router {
	private val navigation = SlotNavigation<Config>()

	val state: Value<ChildSlot<Config, ProjectHome.ContentDestination>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.Stats },
			key = "ProjectHomeContentRouter",
			childFactory = ::createChild,
			serializer = Config.serializer(),
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is ProjectHome.ContentDestination.Stats
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectHome.ContentDestination =
		when (config) {
			Config.Stats -> ProjectHome.ContentDestination.Stats
			Config.ProjectSettings -> ProjectHome.ContentDestination.ProjectSettings(
				ProjectSettingsComponent(
					componentContext = componentContext,
					projectDef = projectDef,
				)
			)
		}

	fun showProjectStats() {
		navigation.activate(Config.Stats)
	}

	fun showProjectSettings() {
		navigation.activate(Config.ProjectSettings)
	}

	@Serializable
	sealed class Config {
		@Serializable
		data object Stats : Config()

		@Serializable
		data object ProjectSettings : Config()
	}
}
