package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface AccountSettings {
	val showProjectDirectory: Boolean
	val state: Value<State>

	fun setProjectsDir(path: String)
	fun setUiTheme(theme: UiTheme)
	suspend fun reinstallExampleProject()
	fun beginSetupServer()
	fun cancelServerSetup()
	suspend fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean
	): Result<Boolean>

	suspend fun authTest(): Boolean
	fun removeServer()

	suspend fun setAutomaticBackups(value: Boolean)
	suspend fun setAutoCloseDialogs(value: Boolean)
	suspend fun setAutoSyncing(value: Boolean)
	suspend fun setMaxBackups(value: Int)

	data class State(
		val projectsDir: HPath,
		val location: ProjectSelection.Locations = ProjectSelection.Locations.Projects,
		val uiTheme: UiTheme,
		val serverSetup: Boolean = false,
		val serverUrl: String? = null,
		val serverError: String? = null,
		val syncAutomaticSync: Boolean,
		val syncAutomaticBackups: Boolean,
		val syncAutoCloseDialog: Boolean,
		val maxBackups: Int
	)
}