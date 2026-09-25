package com.darkrockstudios.apps.hammer.common.projectselection

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManager
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManagerConfig
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.Locale
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
		override val backupManagerSlot: Value<ChildSlot<BackupManagerConfig, BackupManager>> =
			MutableValue(ChildSlot(child = null))

		override fun setUiTheme(theme: UiTheme) {}
		override fun setInitialProjectScreen(value: InitialProjectScreen) {}
		override fun reinstallExampleProject(onComplete: (Boolean) -> Unit) {}
		override fun beginSetupServer() {}
		override fun cancelServerSetup() {}
		override fun setupServer(
			url: String,
			email: String,
			password: String,
			create: Boolean,
			replaceLocalContent: Boolean
		) {
		}

		override fun chooseMerge() {}
		override fun chooseReplace() {}
		override fun cancelMergePrompt() {}

		override fun acceptTos() {}
		override fun declineTos() {}

		override suspend fun authTest() = true
		override suspend fun removeServer() {}
		override suspend fun setAutomaticBackups(value: Boolean) {}
		override suspend fun setAutoCloseDialogs(value: Boolean) {}
		override suspend fun setAutoSyncing(value: Boolean) {}
		override suspend fun setMaxBackups(value: Int) = true
		override fun reauthenticate() {}
		override fun updateServerUrl(url: String) {}
		override fun updateServerEmail(email: String) {}
		override fun updateServerPassword(password: String) {}
		override val spellCheckSettings: SpellCheckSettings
			get() = object : SpellCheckSettings {
				override val state: Value<SpellCheckSettings.State>
					get() = MutableValue(
						SpellCheckSettings.State(
							true, true, true, Locale.root, emptyList()
						)
					)

				override suspend fun setSpellcheckEnable(enable: Boolean) {}
				override suspend fun setSpellCheckingInFocusEnabled(enable: Boolean) {}
				override suspend fun setSpellCheckEncyclopediaEnabled(enable: Boolean) {}
				override suspend fun setSpellCheckLanguage(language: Locale) {}
			}

		override fun showBackupManager() {}
		override fun dismissBackupManager() {}

		override val toast = MutableSharedFlow<ToastMessage>()
		override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
		override fun showToast(scope: CoroutineScope, message: String) {}
		override fun showToast(scope: CoroutineScope, message: Msg) {}
		override suspend fun showToast(message: StringResource, vararg params: Any) {}
		override suspend fun showToast(message: String) {}
		override suspend fun showToast(message: Msg) {}
	}