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
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubReleaseInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
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
	private val settingsRepository: GlobalSettingsRepository by inject()
	private val versionCheckRepository: VersionCheckRepository by inject()

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

	private val _updateNotification = MutableValue(ProjectSelection.UpdateNotificationState())
	override val updateNotification: Value<ProjectSelection.UpdateNotificationState> = _updateNotification

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

		scope.launch {
			val result = versionCheckRepository.checkForUpdate()
			val release = result.latestRelease
			val dismissed = settingsRepository.globalSettings.lastDismissedUpdateVersion
			if (result.isNewVersionAvailable && release != null && release.tagName != dismissed) {
				withContext(dispatcherMain) {
					_updateNotification.update {
						release.toNotificationState(
							isNewVersionAvailable = result.isNewVersionAvailable,
							manuallyTriggered = false,
						)
					}
				}
			}
		}
	}

	override fun showCurrentReleaseDetails() {
		val result = versionCheckRepository.currentResult() ?: return
		val release = result.latestRelease ?: return
		val next = release.toNotificationState(
			isNewVersionAvailable = result.isNewVersionAvailable,
			manuallyTriggered = true,
		)
		if (next == _updateNotification.value) return
		_updateNotification.update { next }
	}

	private fun GithubReleaseInfo.toNotificationState(
		isNewVersionAvailable: Boolean,
		manuallyTriggered: Boolean,
	) = ProjectSelection.UpdateNotificationState(
		visible = true,
		latestVersionTag = tagName,
		releaseName = name,
		releaseBody = body,
		releaseUrl = htmlUrl,
		isNewVersionAvailable = isNewVersionAvailable,
		manuallyTriggered = manuallyTriggered,
	)

	override fun toggleNavRailExpanded() {
		scope.launch {
			settingsRepository.updateSettings { it.copy(navRailExpanded = !it.navRailExpanded) }
		}
	}

	override fun openReleaseUrl() {
		_updateNotification.value.releaseUrl?.let { urlLauncher.openInBrowser(it) }
	}

	override fun dismissUpdateNotification(remember: Boolean) {
		val tag = _updateNotification.value.latestVersionTag
		_updateNotification.update { it.copy(visible = false) }
		if (remember && tag != null) {
			scope.launch {
				settingsRepository.updateSettings { it.copy(lastDismissedUpdateVersion = tag) }
			}
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
					)
				)
			}

			ProjectSelection.Config.AboutApp -> {
				ProjectSelection.Destination.AboutAppDestination(
					AboutAppComponent(
						componentContext = componentContext,
						urlLauncher = urlLauncher,
						updateShouldClose = { navigation.pop() },
						versionCheckRepository = versionCheckRepository,
						onShowReleaseDetails = ::showCurrentReleaseDetails,
					)
				)
			}
		}

	override fun showLocation(location: ProjectSelection.Locations) {
		when (location) {
			ProjectSelection.Locations.Projects -> navigation.bringToFront(ProjectSelection.Config.ProjectsList)
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
