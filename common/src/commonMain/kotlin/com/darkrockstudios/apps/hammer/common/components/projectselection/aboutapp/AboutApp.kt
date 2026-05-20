package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import kotlinx.serialization.Serializable


interface AboutApp {
	val state: Value<State>

	fun openDiscord()
	fun openReddit()
	fun openGithub()
	fun viewReleaseDetails()

	@Serializable
	data class State(
		val latestVersion: String? = null,
		val currentVersion: String = getAppVersionString(),
		val newVersionAvailable: Boolean = false,
		val logDirectoryPath: String = "",
	)

}