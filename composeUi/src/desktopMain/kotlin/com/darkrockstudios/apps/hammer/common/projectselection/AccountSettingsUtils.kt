package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.fluidsonic.locale.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.StringResource

val defaultAccountSettingsComponentState = AccountSettings.State(
	location = ProjectSelection.Locations.Settings,
	uiTheme = UiTheme.FollowSystem,
	serverSetup = true,
	serverUrl = null,
	serverError = null,
	serverWorking = false,
	syncAutomaticSync = true,
	syncAutomaticBackups = true,
	syncAutoCloseDialog = true,
	maxBackups = 50,
)

internal fun accountSettingsComponent(state: AccountSettings.State = defaultAccountSettingsComponentState) =
	object : AccountSettings {
		override val state = MutableValue(state)
		override val platformSettings = object : DesktopPlatformSettings {
			override val state = MutableValue(
				DesktopPlatformSettings.PlatformState(
					projectsDir = HPath(
						path = "/home/user/projects",
						name = "Projects",
						isAbsolute = true
					)
				)
			)

			override fun setProjectsDir(path: String) {}
		}
		override fun setUiTheme(theme: UiTheme) {}
		override fun reinstallExampleProject(onComplete: (Boolean) -> Unit) {}
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
		override val spellCheckSettings: SpellCheckSettings
			get() = object : SpellCheckSettings {
				override val state: Value<SpellCheckSettings.State>
					get() = MutableValue(
						SpellCheckSettings.State(
							true, true, Locale.root, emptyList()
						)
					)

				override suspend fun setSpellcheckEnable(enable: Boolean) {}
				override suspend fun setSpellCheckingInFocusEnabled(enable: Boolean) {}
				override suspend fun setSpellCheckLanguage(language: Locale) {}
			}
		override val toast = MutableSharedFlow<ToastMessage>()
		override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
		override fun showToast(scope: CoroutineScope, message: Msg) {}
		override suspend fun showToast(message: StringResource, vararg params: Any) {}
		override suspend fun showToast(message: Msg) {}
	}