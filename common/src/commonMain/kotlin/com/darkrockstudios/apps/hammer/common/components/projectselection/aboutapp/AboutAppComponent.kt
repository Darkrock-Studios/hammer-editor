package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.DISCORD_URL
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.base.REDDIT_URL
import com.darkrockstudios.apps.hammer.base.VERSION_CHECK_URL
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import com.darkrockstudios.apps.hammer.common.util.isNewVersionAvailable
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.awt.Desktop

class AboutAppComponent(
	componentContext: ComponentContext,
	private val urlLauncher: UrlLauncher,
	private val updateShouldClose: () -> Unit,
	private val http: HttpClient,
) : AboutApp, ComponentBase(componentContext) {

	private val _state = MutableValue(AboutApp.State())
	override val state: Value<AboutApp.State> = _state

	init {
		scope.launch {
			try {
				val response = http.get(VERSION_CHECK_URL)
				val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
				val latestTag = json["tag_name"]?.jsonPrimitive?.content
				if (latestTag != null) {
					_state.update {
						it.copy(
							latestVersion = latestTag,
							newVersionAvailable = isNewVersionAvailable(latestTag)
						)
					}
				}
			} catch (e: Exception) {
				Napier.w("Failed to check latest app version", e)
			}
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

	override fun openLogDirectory() {
		com.darkrockstudios.apps.hammer.common.getLogDirectory()?.let { logDir ->
			try {
				val dir = File(logDir)
				if (dir.exists() && Desktop.isDesktopSupported()) {
					Desktop.getDesktop().open(dir)
				}
			} catch (e: Exception) {
				Napier.e("Failed to open log directory", e)
			}
		}
	}
}