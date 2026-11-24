package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.intro.IntroComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutAppComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsListComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeasComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.nux.ResolveIntroUseCase
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.base.RELEASES_LATEST_URL
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import com.darkrockstudios.apps.hammer.common.data.changelog.Changelog
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogRepository
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectSelectionComponent(
	componentContext: ComponentContext,
	private val onProjectSelected: (projectDef: ProjectDef) -> Unit
) : ProjectSelection, ComponentBase(componentContext) {

	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val urlLauncher: UrlLauncher by inject()
	private val settingsRepository: GlobalSettingsStore by inject()
	private val changelogRepository: ChangelogRepository by inject()
	private val appUpdater: AppUpdater by inject()
	private val resolveIntro: ResolveIntroUseCase by inject()

	private val navigation = StackNavigation<ProjectSelection.Config>()
	override val stack = childStack(
		source = navigation,
		initialConfiguration = ProjectSelection.Config.ProjectsList,
		handleBackButton = false,
		serializer = ProjectSelection.Config.serializer(),
		childFactory = ::createChild
	)

	private val introNavigation = SlotNavigation<ProjectSelection.IntroConfig>()
	private val introSlot = childSlot(
		source = introNavigation,
		serializer = ProjectSelection.IntroConfig.serializer(),
		key = "intro",
		handleBackButton = false,
	) { _, componentContext ->
		IntroComponent(componentContext, onDone = { introNavigation.dismiss() })
	}

	/** Read before this launch installs the example project. */
	private val freshInstall = exampleProjectRepository.shouldInstallFirstTime()

	/**
	 * An existing install starts Done, so a returning user never waits on a blank page; if they turn
	 * out to be new after all, the intro opens over the list.
	 */
	private val _introStage = MutableValue(
		if (freshInstall) ProjectSelection.IntroStage.Deciding else ProjectSelection.IntroStage.Done
	)
	override val introStage: Value<ProjectSelection.IntroStage> = _introStage

	init {
		introSlot.subscribe { slot ->
			val intro = slot.child?.instance
			when {
				intro != null -> _introStage.value = ProjectSelection.IntroStage.Showing(intro)
				_introStage.value is ProjectSelection.IntroStage.Showing -> _introStage.value = ProjectSelection.IntroStage.Done
			}
		}
	}

	private val _navRailState = MutableValue(
		ProjectSelection.NavRailState(expanded = settingsRepository.globalSettings.navRailExpanded)
	)
	override val navRailState: Value<ProjectSelection.NavRailState> = _navRailState

	private val _changelog = MutableValue(ProjectSelection.ChangelogState())
	override val changelog: Value<ProjectSelection.ChangelogState> = _changelog

	private val _appUpdate = MutableValue<AppUpdateState>(appUpdater.state.value)
	override val appUpdate: Value<AppUpdateState> = _appUpdate

	init {
		if (freshInstall) {
			exampleProjectRepository.install(projectsRepository.findProject(ExampleProjectRepository.PROJECT_NAME)?.path)
		}
		decideIntro()

		scope.launch {
			appUpdater.state.collect { update ->
				withContext(dispatcherMain) {
					_appUpdate.update { update }
				}
			}
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
	}

	private fun decideIntro() {
		scope.launch {
			val newUser = showIntroIfNew()
			// Only a brand new install has nothing to compare against; anyone upgrading still gets What's new.
			settleChangelog(suppress = freshInstall && newUser)
		}
	}

	/** Opens the intro for a new user. False when that cannot be told; the stage leaves Deciding either way. */
	@Suppress("TooGenericExceptionCaught") // Whatever goes wrong, the screen must move on to the project list.
	private suspend fun showIntroIfNew(): Boolean {
		if (introSlot.value.child != null) return true
		try {
			val newUser = try {
				withContext(dispatcherIo) { resolveIntro() }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Could not tell whether to show the intro, so leaving it out.", e)
				false
			}
			if (newUser) withContext(dispatcherMain) { introNavigation.activate(ProjectSelection.IntroConfig) }
			return newUser
		} finally {
			withContext(NonCancellable + dispatcherMain) {
				if (_introStage.value == ProjectSelection.IntroStage.Deciding) _introStage.value = ProjectSelection.IntroStage.Done
			}
		}
	}

	/** Marks this release's changelog seen when [suppress], else shows it if unseen. Baked in, so no network. */
	private suspend fun settleChangelog(suppress: Boolean) {
		if (suppress) {
			changelogRepository.markSeen()
		} else if (changelogRepository.hasUnseenChangelog()) {
			val changelog = changelogRepository.getChangelog() ?: return
			withContext(dispatcherMain) {
				_changelog.update { changelog.toState() }
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

	override fun updateApp() {
		appUpdater.update()
	}

	override fun dismissUpdate() {
		appUpdater.dismiss()
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
