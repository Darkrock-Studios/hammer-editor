package com.darkrockstudios.apps.hammer.common.components.iosroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.runBlocking
import org.koin.core.component.getScopeId

class IosRootComponent(
	componentContext: ComponentContext,
	): ComponentBase(componentContext), IosRoot {

	private val navigation = SlotNavigation<IosRoot.Config>()
	override val slot = componentContext.childSlot(
		source = navigation,
		serializer = IosRoot.Config.serializer(),
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
				componentContext.lifecycle.doOnDestroy {
					val scopeId = ProjectDefScope(config.projectDef).getScopeId()
					getKoin().getScopeOrNull(scopeId)?.let { scope ->
						closeProjectScope(scope, config.projectDef)
					}
				}
				IosRoot.Destination.ProjectRootDestination(
					ProjectRootComponent(
						componentContext = componentContext,
						projectDef = config.projectDef,
						addMenu = {},
						removeMenu = {},
						onCloseProject = ::closeProject,
					)
				)
			}
			is IosRoot.Config.ProjectSelect -> {
				IosRoot.Destination.ProjectSelectDestination(
					ProjectSelectionComponent(
						componentContext = componentContext,
						onProjectSelected = ::goToProject,
					)
				)
			}
		}

	private fun goToProject(projectDef: ProjectDef) {
		// Matches the Android pattern: open is blocking because downstream
		// components need a ready scope; the destroy hook in createChild
		// closes it when the slot replaces this destination.
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