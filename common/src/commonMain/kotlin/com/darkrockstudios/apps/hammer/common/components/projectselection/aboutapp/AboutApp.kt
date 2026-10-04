package com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


interface AboutApp {
	val state: Value<State>

	fun openDiscord()
	fun openReddit()
	fun openGithub()
	fun viewChangelog()
	fun openLatestRelease()

	fun checkForUpdate()
	fun updateApp()

	@Serializable
	data class State(
		val currentVersion: String = getAppVersionString(),
		val logDirectoryPath: String = "",
		@Transient val appUpdate: AppUpdateState = AppUpdateState.Unsupported,
	)

}
