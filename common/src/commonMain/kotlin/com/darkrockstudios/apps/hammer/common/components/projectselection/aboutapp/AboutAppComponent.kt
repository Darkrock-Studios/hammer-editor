package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.base.DISCORD_URL
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.base.REDDIT_URL
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher

class AboutAppComponent(
	componentContext: ComponentContext,
	private val urlLauncher: UrlLauncher
) : AboutApp, ComponentBase(componentContext) {
	override fun openDiscord() {
		urlLauncher.openInBrowser(DISCORD_URL)
	}

	override fun openReddit() {
		urlLauncher.openInBrowser(REDDIT_URL)
	}

	override fun openGithub() {
		urlLauncher.openInBrowser(GITHUB_URL)
	}
}