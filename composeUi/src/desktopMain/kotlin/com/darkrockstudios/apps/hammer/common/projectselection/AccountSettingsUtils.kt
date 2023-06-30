package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath

val defaultAccountSettingsComponentState = AccountSettings.State(
	projectsDir = HPath("/test", "test", true),
	location = ProjectSelection.Locations.Settings,
	uiTheme = UiTheme.FollowSystem,
	serverSetup = true,
	serverUrl = null,
	serverError = null,
	serverWorking = false,
	syncAutomaticSync = true,
	syncAutomaticBackups = true,
	syncAutoCloseDialog = true,
	maxBackups = 50
)

internal fun accountSettingsComponent(state: AccountSettings.State = defaultAccountSettingsComponentState) =
	object : AccountSettings {
		override val showProjectDirectory: Boolean = true
		override val state = MutableValue(state)

		override fun setProjectsDir(path: String) {}
		override fun setUiTheme(theme: UiTheme) {}
		override suspend fun reinstallExampleProject() {}
		override fun beginSetupServer() {}
		override fun cancelServerSetup() {}
		override fun setupServer(
			ssl: Boolean,
			url: String,
			email: String,
			password: String,
			create: Boolean,
			removeLocalContent: Boolean
		) {
		}

		override suspend fun authTest() = true
		override fun removeServer() {}
		override suspend fun setAutomaticBackups(value: Boolean) {}
		override suspend fun setAutoCloseDialogs(value: Boolean) {}
		override suspend fun setAutoSyncing(value: Boolean) {}
		override suspend fun setMaxBackups(value: Int) {}
		override fun reauthenticate() {}
		override fun updateServerUrl(url: String) {}
		override fun updateServerSsl(ssl: Boolean) {}
		override fun updateServerEmail(email: String) {}
		override fun updateServerPassword(password: String) {}
	}