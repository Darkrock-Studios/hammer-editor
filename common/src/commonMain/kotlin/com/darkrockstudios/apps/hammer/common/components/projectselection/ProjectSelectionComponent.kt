package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutAppComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsListComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeasComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.base.RELEASES_LATEST_URL
import com.darkrockstudios.apps.hammer.common.data.changelog.Changelog
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogRepository
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectSelectionComponent(
	componentContext: ComponentContext,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {

	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val urlLauncher: UrlLauncher by inject()
	private val settingsRepository: GlobalSettingsStore by inject()
	private val changelogRepository: ChangelogRepository by inject()

	private val navigation = StackNavigation<ProjectSelection.Config>()
	override val stack = childStack(
		source = navigation,
		initialConfiguration = ProjectSelection.Config.ProjectsList,
		handleBackButton = false,
		serializer = ProjectSelection.Config.serializer(),
		childFactory = ::createChild
	)

	private val _navRailState = MutableValue(
		ProjectSelection.NavRailState(expanded = settingsRepository.globalSettings.navRailExpanded)
	)
	override val navRailState: Value<ProjectSelection.NavRailState> = _navRailState

	private val _changelog = MutableValue(ProjectSelection.ChangelogState())
	override val changelog: Value<ProjectSelection.ChangelogState> = _changelog

	init {
		if (exampleProjectRepository.shouldInstallFirstTime()) {
			exampleProjectRepository.install()
		}

		scope.launch {
			settingsRepository.globalSettingsUpdates.collect { settings ->
				if (_navRailState.value.expanded != settings.navRailExpanded) {
					withContext(dispatcherMain) {
						_navRailState.update { it.copy(expanded = settings.navRailExpanded) }
					}
				}
			}
		}

		// Read from the changelog baked in at release time — nothing here touches the network.
		scope.launch {
			if (changelogRepository.hasUnseenChangelog()) {
				val changelog = changelogRepository.getChangelog() ?: return@launch
				withContext(dispatcherMain) {
					_changelog.update { changelog.toState() }
				}
			}
		}
	}

	override fun showChangelog() {
		scope.launch {
			val changelog = changelogRepository.getChangelog() ?: return@launch
			withContext(dispatcherMain) {
				_changelog.update { changelog.toState() }
			}
		}
	}

	private fun Changelog.toState() = ProjectSelection.ChangelogState(
		visible = true,
		version = version,
		date = date,
		notes = notes,
	)

	override fun toggleNavRailExpanded() {
		scope.launch {
			settingsRepository.updateSettings { it.copy(navRailExpanded = !it.navRailExpanded) }
		}
	}

	override fun openLatestRelease() {
		urlLauncher.openInBrowser(RELEASES_LATEST_URL)
	}

	override fun dismissChangelog() {
		_changelog.update { it.copy(visible = false) }
		scope.launch { changelogRepository.markSeen() }
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

			ProjectSelection.Config.StoryIdeas -> {
				ProjectSelection.Destination.StoryIdeasDestination(
					StoryIdeasComponent(
						componentContext,
					)
				)
			}

			ProjectSelection.Config.AccountSettings -> {
				ProjectSelection.Destination.AccountSettingsDestination(
					AccountSettingsComponent(
						componentContext,
					)
				)
			}

			ProjectSelection.Config.AboutApp -> {
				ProjectSelection.Destination.AboutAppDestination(
					AboutAppComponent(
						componentContext = componentContext,
						urlLauncher = urlLauncher,
						updateShouldClose = { navigation.pop() },
						onShowChangelog = ::showChangelog,
					)
				)
			}
		}

	override fun showLocation(location: ProjectSelection.Locations) {
		when (location) {
			ProjectSelection.Locations.Projects -> navigation.bringToFront(ProjectSelection.Config.ProjectsList)
			ProjectSelection.Locations.StoryIdeas -> navigation.bringToFront(ProjectSelection.Config.StoryIdeas)
			ProjectSelection.Locations.Settings -> navigation.bringToFront(ProjectSelection.Config.AccountSettings)
			ProjectSelection.Locations.AboutApp -> navigation.bringToFront(ProjectSelection.Config.AboutApp)
		}
	}

	override fun isAtRoot(): Boolean {
		return stack.value.backStack.isEmpty()
	}

	override fun onBack() {
		navigation.pop()
	}
}
