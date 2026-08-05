package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.DISCORD_URL
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.base.REDDIT_URL
import com.darkrockstudios.apps.hammer.base.RELEASES_LATEST_URL
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getLogDirectory
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher

class AboutAppComponent(
	componentContext: ComponentContext,
	private val urlLauncher: UrlLauncher,
	private val updateShouldClose: () -> Unit,
	private val onShowChangelog: () -> Unit,
) : AboutApp, ComponentBase(componentContext) {

	private val _state = MutableValue(AboutApp.State(logDirectoryPath = getLogDirectoryPath()))
	override val state: Value<AboutApp.State> = _state

	override fun openDiscord() {
		urlLauncher.openInBrowser(DISCORD_URL)
	}

	override fun openReddit() {
		urlLauncher.openInBrowser(REDDIT_URL)
	}

	override fun openGithub() {
		urlLauncher.openInBrowser(GITHUB_URL)
	}

	override fun viewChangelog() {
		onShowChangelog()
	}

	override fun openLatestRelease() {
		urlLauncher.openInBrowser(RELEASES_LATEST_URL)
	}

	private fun getLogDirectoryPath(): String {
		return getLogDirectory() ?: (getConfigDirectory() + "/logs")
	}
}
