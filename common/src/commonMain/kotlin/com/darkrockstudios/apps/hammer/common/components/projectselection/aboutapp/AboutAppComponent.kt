package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.DISCORD_URL
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.base.REDDIT_URL
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getLogDirectory
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutAppComponent(
	componentContext: ComponentContext,
	private val urlLauncher: UrlLauncher,
	private val updateShouldClose: () -> Unit,
	private val versionCheckRepository: VersionCheckRepository,
	private val onShowReleaseDetails: () -> Unit,
) : AboutApp, ComponentBase(componentContext) {

	private val _state = MutableValue(AboutApp.State(logDirectoryPath = getLogDirectoryPath()))
	override val state: Value<AboutApp.State> = _state

	init {
		scope.launch {
			versionCheckRepository.updates.collect { result ->
				val tag = result.latestRelease?.tagName
				withContext(dispatcherMain) {
					_state.update {
						it.copy(
							latestVersion = tag,
							newVersionAvailable = result.isNewVersionAvailable,
						)
					}
				}
			}
		}

		if (versionCheckRepository.currentResult() == null) {
			scope.launch { versionCheckRepository.checkForUpdate() }
		}
	}

	override fun openDiscord() {
		urlLauncher.openInBrowser(DISCORD_URL)
	}

	override fun openReddit() {
		urlLauncher.openInBrowser(REDDIT_URL)
	}

	override fun openGithub() {
		urlLauncher.openInBrowser(GITHUB_URL)
	}

	override fun viewReleaseDetails() {
		onShowReleaseDetails()
	}

	private fun getLogDirectoryPath(): String {
		return getLogDirectory() ?: (getConfigDirectory() + "/logs")
	}
}
