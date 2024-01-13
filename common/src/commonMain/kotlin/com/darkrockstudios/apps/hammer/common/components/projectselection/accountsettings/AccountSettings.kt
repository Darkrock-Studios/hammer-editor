package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface AccountSettings : ComponentToaster {
	val showProjectDirectory: Boolean
	val state: Value<State>
	val platformSettings: PlatformSettings

	fun setProjectsDir(path: String)
	fun setUiTheme(theme: UiTheme)
	suspend fun reinstallExampleProject()
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

	@Parcelize
	data class State(
		val projectsDir: HPath,
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
	) : Parcelable
}