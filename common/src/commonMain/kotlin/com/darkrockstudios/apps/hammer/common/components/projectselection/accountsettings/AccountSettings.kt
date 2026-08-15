package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface AccountSettings : ComponentToaster {
	val state: Value<State>
	val platformSettings: PlatformSettings
	val backupManagerSlot: Value<ChildSlot<BackupManagerConfig, BackupManager>>

	fun setUiTheme(theme: UiTheme)
	fun setInitialProjectScreen(value: InitialProjectScreen)
	fun reinstallExampleProject(onComplete: (Boolean) -> Unit)
	fun beginSetupServer()
	fun cancelServerSetup()
	/**
	 * [replaceLocalContent] is only ever true when the user explicitly chose "Replace" in the merge
	 * prompt. When it is false and the login would merge real local work into a different server,
	 * this raises that prompt instead of running the setup; see [chooseMerge] / [chooseReplace].
	 */
	fun setupServer(
		url: String,
		email: String,
		password: String,
		create: Boolean,
		replaceLocalContent: Boolean
	)

	fun chooseMerge()
	fun chooseReplace()
	fun cancelMergePrompt()

	fun acceptTos()
	fun declineTos()

	suspend fun authTest(): Boolean
	suspend fun removeServer()

	suspend fun setAutomaticBackups(value: Boolean)
	suspend fun setAutoCloseDialogs(value: Boolean)
	suspend fun setAutoSyncing(value: Boolean)
	suspend fun setMaxBackups(value: Int): Boolean
	fun reauthenticate()
	fun updateServerUrl(url: String)
	fun updateServerEmail(email: String)
	fun updateServerPassword(password: String)

	val spellCheckSettings: SpellCheckSettings

	fun showBackupManager()
	fun dismissBackupManager()

	@Serializable
	data class State(
		val location: ProjectSelection.Locations = ProjectSelection.Locations.Projects,
		val uiTheme: UiTheme,
		val currentUserId: Long? = null,
		val currentUrl: String? = null,
		val currentEmail: String? = null,
		val serverSetup: Boolean = false,
		val serverIsLoggedIn: Boolean = false,
		val serverUrl: String? = null,
		val serverEmail: String? = null,
		// Never persist the password to disk; drop stale error/working state on restore.
		@Transient val serverPassword: String? = null,
		@Transient val serverError: String? = null,
		@Transient val serverWorking: Boolean = false,
		@Transient val tosChallenge: TermsOfServiceChallenge? = null,
		@Transient val mergePrompt: Boolean = false,
		val syncAutomaticSync: Boolean,
		val syncAutomaticBackups: Boolean,
		val syncAutoCloseDialog: Boolean,
		val maxBackups: Int,
		val initialProjectScreen: InitialProjectScreen = InitialProjectScreen.Home,
	)
}
