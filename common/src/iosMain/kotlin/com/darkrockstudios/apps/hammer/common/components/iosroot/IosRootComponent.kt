package com.darkrockstudios.apps.hammer.common.components.iosroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import kotlinx.coroutines.runBlocking

class IosRootComponent(
	componentContext: ComponentContext,
	): ComponentBase(componentContext), IosRoot {

	private val navigation = SlotNavigation<IosRoot.Config>()
	override val slot = componentContext.childSlot(
		source = navigation,
		initialConfiguration = { IosRoot.Config.ProjectSelect },
		handleBackButton = false,
	) { config, componentContext ->
		createChild(config, componentContext)
	}

	private fun createChild(
		config: IosRoot.Config,
		componentContext: ComponentContext
	): IosRoot.Destination =
		when (config) {
			is IosRoot.Config.ProjectRoot -> {
				IosRoot.Destination.ProjectRootDestination(
					ProjectRootComponent(
						componentContext = componentContext,
						projectDef = config.projectDef,
						addMenu = {},
						removeMenu = {},
					)
				)
			}
			is IosRoot.Config.ProjectSelect -> {
				IosRoot.Destination.ProjectSelectDestination(
					ProjectSelectionComponent(
						componentContext = componentContext,
						showProjectDirectory = false,
						onProjectSelected = ::goToProject,
					)
				)
			}
		}

	private fun goToProject(projectDef: ProjectDef) {
		// TODO how should we manage this on iOS? Needs to be tied to lifecycle
		runBlocking {
			openProjectScope(projectDef)
		}
		
		navigation.activate(
			IosRoot.Config.ProjectRoot(projectDef)
		)
	}

	override fun closeProject() {
		navigation.activate(IosRoot.Config.ProjectSelect)
	}
}