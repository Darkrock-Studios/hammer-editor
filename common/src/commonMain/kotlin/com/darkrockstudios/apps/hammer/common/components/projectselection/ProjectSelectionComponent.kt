package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsListComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import org.koin.core.component.inject

class ProjectSelectionComponent(
	componentContext: ComponentContext,
	override val showProjectDirectory: Boolean = false,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {

	private val exampleProjectRepository: ExampleProjectRepository by inject()

	private val navigation = SlotNavigation<ProjectSelection.Config>()
	override val slot = componentContext.childSlot(
		source = navigation,
		initialConfiguration = { ProjectSelection.Config.ProjectsList },
		handleBackButton = false,
	) { config, componentContext ->
		createChild(config, componentContext)
	}

	init {
		if (exampleProjectRepository.shouldInstallFirstTime()) {
			exampleProjectRepository.install()
		}
	}

	private fun createChild(
		config: ProjectSelection.Config,
		componentContext: ComponentContext
	): ProjectSelection.Destination =
		when (config) {
			ProjectSelection.Config.ProjectsList -> {
				ProjectSelection.Destination.ProjectsListDestination(
					ProjectsListComponent(
						componentContext,
						onProjectSelected
					)
				)
			}

			ProjectSelection.Config.AccountSettings -> {
				ProjectSelection.Destination.AccountSettingsDestination(
					AccountSettingsComponent(
						componentContext,
						showProjectDirectory
					)
				)
			}
		}

	override fun showLocation(location: ProjectSelection.Locations) {
		when (location) {
			ProjectSelection.Locations.Projects -> navigation.activate(ProjectSelection.Config.ProjectsList)
			ProjectSelection.Locations.Sittings -> navigation.activate(ProjectSelection.Config.AccountSettings)
		}
	}
}