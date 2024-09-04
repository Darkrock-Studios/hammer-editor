package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import kotlinx.serialization.Serializable

interface AccountSettings : ComponentToaster {
	val state: Value<State>
	val platformSettings: PlatformSettings

	fun setUiTheme(theme: UiTheme)
	fun reinstallExampleProject(onComplete: (Boolean) -> Unit)
	fun beginSetupServer()
	fun cancelServerSetup()
	fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean,
		removeLocalContent: Boolean
	)

	suspend fun authTest(): Boolean
	fun removeServer()

	suspend fun setAutomaticBackups(value: Boolean)
	suspend fun setAutoCloseDialogs(value: Boolean)
	suspend fun setAutoSyncing(value: Boolean)
	suspend fun setMaxBackups(value: Int)
	fun reauthenticate()
	fun updateServerUrl(url: String)
	fun updateServerSsl(ssl: Boolean)
	fun updateServerEmail(email: String)
	fun updateServerPassword(password: String)

	@Serializable
	data class State(
		val location: ProjectSelection.Locations = ProjectSelection.Locations.Projects,
		val uiTheme: UiTheme,
		val currentSsl: Boolean? = null,
		val currentUrl: String? = null,
		val currentEmail: String? = null,
		val serverSetup: Boolean = false,
		val serverIsLoggedIn: Boolean = false,
		val serverSsl: Boolean = true,
		val serverUrl: String? = null,
		val serverEmail: String? = null,
		val serverPassword: String? = null,
		val serverError: String? = null,
		val serverWorking: Boolean = false,
		val syncAutomaticSync: Boolean,
		val syncAutomaticBackups: Boolean,
		val syncAutoCloseDialog: Boolean,
		val maxBackups: Int,
	)
}