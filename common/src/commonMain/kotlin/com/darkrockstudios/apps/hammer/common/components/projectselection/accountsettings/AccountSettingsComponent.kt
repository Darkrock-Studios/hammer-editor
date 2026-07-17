package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.server_setup_error_invalid_email
import com.darkrockstudios.apps.hammer.server_setup_error_invalid_url
import com.darkrockstudios.apps.hammer.server_setup_error_password_no_lowercase
import com.darkrockstudios.apps.hammer.server_setup_error_password_no_number
import com.darkrockstudios.apps.hammer.server_setup_error_password_no_special
import com.darkrockstudios.apps.hammer.server_setup_error_password_no_uppercase
import com.darkrockstudios.apps.hammer.server_setup_error_password_too_long
import com.darkrockstudios.apps.hammer.server_setup_error_password_too_short
import com.darkrockstudios.apps.hammer.settings_server_setup_toast_failure
import com.darkrockstudios.apps.hammer.settings_server_setup_toast_failure_unknown
import com.darkrockstudios.apps.hammer.settings_server_setup_toast_success
import com.darkrockstudios.apps.hammer.settings_server_tos_declined
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class AccountSettingsComponent(
	componentContext: ComponentContext,
) : AccountSettings,
	ComponentToaster by ComponentToasterImpl(),
	SavableComponent<AccountSettings.State>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()
	private val strRes: StrRes by inject()

	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val accountUseCase: AccountUseCase by inject()
	private val projectsRepository: ProjectsRepository by inject()

	private val backupManagerNavigation = SlotNavigation<BackupManagerConfig>()

	override val backupManagerSlot: Value<ChildSlot<BackupManagerConfig, BackupManager>> =
		componentContext.childSlot(
			source = backupManagerNavigation,
			serializer = BackupManagerConfig.serializer(),
			key = "BackupManagerSlot",
			childFactory = { _, childContext ->
				BackupManagerComponent(childContext)
			}
		)

	private var serverSetupJob: Job? = null
	private var pendingServerSetup: PendingServerSetup? = null

	override val platformSettings: PlatformSettings by inject { parametersOf(componentContext) }
	override val spellCheckSettings: SpellCheckSettings = SpellCheckSettingsComponent(componentContext)

	private val _state by savableState {
		AccountSettings.State(
			uiTheme = globalSettingsStore.globalSettings.uiTheme,
			syncAutomaticSync = globalSettingsStore.globalSettings.automaticSyncing,
			syncAutoCloseDialog = globalSettingsStore.globalSettings.autoCloseSyncDialog,
			syncAutomaticBackups = globalSettingsStore.globalSettings.automaticBackups,
			maxBackups = globalSettingsStore.globalSettings.maxBackups,
			initialProjectScreen = globalSettingsStore.globalSettings.initialProjectScreen,
		)
	}
	override val state: Value<AccountSettings.State> = _state
	override fun getStateSerializer() = AccountSettings.State.serializer()

	init {
		watchSettingsUpdates()
	}

	private fun cancelSetupJob() {
		serverSetupJob?.cancel()
		serverSetupJob = null
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsStore.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							uiTheme = settings.uiTheme,
							syncAutomaticSync = settings.automaticSyncing,
							syncAutoCloseDialog = settings.autoCloseSyncDialog,
							syncAutomaticBackups = settings.automaticBackups,
							maxBackups = settings.maxBackups,
							initialProjectScreen = settings.initialProjectScreen,
						)
					}
				}
			}
		}

		scope.launch {
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							currentUserId = settings?.userId,
							currentUrl = settings?.url,
							currentEmail = settings?.email,
							serverIsLoggedIn = settings?.bearerToken?.isNotBlank() == true,
						)
					}
				}
			}
		}
	}

	override fun setUiTheme(theme: UiTheme) {
		scope.launch {
			globalSettingsStore.updateSettings {
				it.copy(
					uiTheme = theme
				)
			}
		}
	}

	override fun setInitialProjectScreen(value: InitialProjectScreen) {
		scope.launch {
			globalSettingsStore.updateSettings {
				it.copy(
					initialProjectScreen = value
				)
			}
		}
	}

	override fun reinstallExampleProject(onComplete: (Boolean) -> Unit) {
		scope.launch {
			exampleProjectRepository.install()
			onComplete(true)
		}
	}

	override fun beginSetupServer() {
		_state.getAndUpdate {
			it.copy(
				serverSetup = true,
				serverUrl = it.currentUrl,
				serverEmail = it.currentEmail,
				serverPassword = null,
			)
		}
	}

	override fun cancelServerSetup() {
		cancelSetupJob()
		cleanUpServerSetup()
		// A pending setup means setupServer already persisted provisional settings; drop them.
		if (pendingServerSetup != null) {
			globalSettingsStore.deleteServerSettings()
		}
		pendingServerSetup = null
		_state.getAndUpdate {
			it.copy(
				serverSetup = false,
				serverError = null,
				serverWorking = false,
				tosChallenge = null,
			)
		}
	}

	private fun cleanUpServerSetup() {
		_state.getAndUpdate {
			it.copy(
				serverUrl = null,
				serverEmail = null,
				serverPassword = null,
			)
		}
	}

	override suspend fun authTest(): Boolean {
		return accountUseCase.testAuth()
	}

	override fun removeServer() {
		globalSettingsStore.deleteServerSettings()
		clearAllProjectIds()
	}

	private fun clearAllProjectIds() {
		projectsRepository.getProjects().forEach { projectDef ->
			projectsRepository.removeProjectId(projectDef = projectDef)
		}
	}

	override suspend fun setAutomaticBackups(value: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				automaticBackups = value
			)
		}
	}

	override suspend fun setAutoCloseDialogs(value: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				autoCloseSyncDialog = value
			)
		}
	}

	override suspend fun setAutoSyncing(value: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				automaticSyncing = value
			)
		}
	}

	override suspend fun setMaxBackups(value: Int): Boolean {
		return if (value in 1..GlobalSettings.MAX_BACKUPS) {
			globalSettingsStore.updateSettings {
				it.copy(
					maxBackups = value
				)
			}
			true
		} else {
			false
		}
	}

	override fun reauthenticate() {
		_state.getAndUpdate {
			it.copy(
				serverSetup = true,
				serverUrl = state.value.currentUrl,
				serverEmail = state.value.currentEmail,
			)
		}
	}

	override fun updateServerUrl(url: String) {
		_state.getAndUpdate { it.copy(serverUrl = url) }
	}

	override fun updateServerEmail(email: String) {
		_state.getAndUpdate { it.copy(serverEmail = email) }
	}

	override fun updateServerPassword(password: String) {
		_state.getAndUpdate { it.copy(serverPassword = password) }
	}

	override fun showBackupManager() {
		backupManagerNavigation.activate(BackupManagerConfig)
	}

	override fun dismissBackupManager() {
		backupManagerNavigation.dismiss()
	}

	override fun setupServer(
		url: String,
		email: String,
		password: String,
		create: Boolean,
		removeLocalContent: Boolean
	) {
		cancelSetupJob()

		serverSetupJob = scope.launch {
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						serverError = null,
						serverWorking = true,
					)
				}
			}

			// Client-side URL validation
			val cleanUrl = cleanUpUrl(url)
			if (validateUrl(cleanUrl).not()) {
				val message = strRes.get(Res.string.server_setup_error_invalid_url)
				showValidationError(message)
				return@launch
			}

			// Client-side email validation
			if (!EmailValidator.validate(email)) {
				val message = strRes.get(Res.string.server_setup_error_invalid_email)
				showValidationError(message)
				return@launch
			}

			// Client-side password validation
			val passwordResult = PasswordValidator.validate(password)
			if (passwordResult != PasswordValidationResult.VALID) {
				val message = getPasswordValidationErrorMessage(passwordResult)
				showValidationError(message)
				return@launch
			}

			// All validation passed, proceed with server setup
			if (removeLocalContent) {
				removeLocalContent()
			}

			val pending = PendingServerSetup(cleanUrl, email.trim(), password, create)
			pendingServerSetup = pending
			performServerSetup(pending, acceptedTosVersion = null)
		}
	}

	override fun acceptTos() {
		val pending = pendingServerSetup ?: return
		val version = _state.value.tosChallenge?.version ?: return

		cancelSetupJob()
		serverSetupJob = scope.launch {
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						tosChallenge = null,
						serverError = null,
						serverWorking = true,
					)
				}
			}
			performServerSetup(pending, acceptedTosVersion = version)
		}
	}

	override fun declineTos() {
		pendingServerSetup = null
		// setupServer persisted provisional settings for the retry; declining discards them.
		globalSettingsStore.deleteServerSettings()
		_state.getAndUpdate {
			it.copy(
				tosChallenge = null,
				serverSetup = false,
				serverWorking = false,
			)
		}
		showToast(scope, Res.string.settings_server_tos_declined)
	}

	private suspend fun performServerSetup(pending: PendingServerSetup, acceptedTosVersion: String?) {
		val result = accountUseCase.setupServer(
			url = pending.url,
			email = pending.email,
			password = pending.password,
			create = pending.create,
			acceptedTosVersion = acceptedTosVersion,
		)
		withContext(mainDispatcher) {
			when (result) {
				is ServerSetupResult.Success -> {
					pendingServerSetup = null
					// A freshly created account holds no projects, so any serverProjectId from a
					// previous server is stale and would make sync skip re-creating the project.
					if (pending.create) {
						clearAllProjectIds()
					}
					cleanUpServerSetup()
					_state.getAndUpdate {
						it.copy(
							serverSetup = false,
							serverWorking = false,
						)
					}
					showToast(Res.string.settings_server_setup_toast_success)
				}

				is ServerSetupResult.TermsRequired -> {
					// Replace the setup dialog with the terms dialog so they don't stack.
					_state.getAndUpdate {
						it.copy(
							tosChallenge = result.challenge,
							serverSetup = false,
							serverWorking = false,
						)
					}
				}

				is ServerSetupResult.Failure -> {
					pendingServerSetup = null
					val message = result.displayMessage?.text(strRes)
						?: strRes.get(Res.string.settings_server_setup_toast_failure_unknown)
					_state.getAndUpdate {
						it.copy(
							serverError = message,
							serverWorking = false,
						)
					}
					showToast(scope, Res.string.settings_server_setup_toast_failure, message)
				}
			}
		}
	}

	private suspend fun showValidationError(message: String) {
		withContext(mainDispatcher) {
			_state.getAndUpdate {
				it.copy(
					serverError = message,
					serverWorking = false,
				)
			}
		}
		showToast(Res.string.settings_server_setup_toast_failure, message)
	}

	private suspend fun getPasswordValidationErrorMessage(result: PasswordValidationResult): String {
		return when (result) {
			PasswordValidationResult.TOO_SHORT -> strRes.get(Res.string.server_setup_error_password_too_short)
			PasswordValidationResult.TOO_LONG -> strRes.get(Res.string.server_setup_error_password_too_long)
			PasswordValidationResult.NO_UPPERCASE -> strRes.get(Res.string.server_setup_error_password_no_uppercase)
			PasswordValidationResult.NO_LOWERCASE -> strRes.get(Res.string.server_setup_error_password_no_lowercase)
			PasswordValidationResult.NO_NUMBER -> strRes.get(Res.string.server_setup_error_password_no_number)
			PasswordValidationResult.NO_SPECIAL -> strRes.get(Res.string.server_setup_error_password_no_special)
			PasswordValidationResult.VALID -> error("Should not pass a successful result")
		}
	}

	private suspend fun removeLocalContent() {
		projectsRepository.getProjects().forEach { projectDef ->
			projectsRepository.deleteProject(projectDef)
		}
	}

	companion object {
		// regex to validate url with port number
		private val urlWithPortRegex =
			Regex("""^([a-z0-9]+\.)*([a-z0-9]+)(\.[a-z]+)(:[0-9]{1,5})?$""")
		private val ipWithPortRegex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?::\d+)?$""")

		fun validateUrl(url: String): Boolean {
			return url.isNotBlank() && (urlWithPortRegex.matches(url) || ipWithPortRegex.matches(url))
		}

		fun cleanUpUrl(url: String): String {
			var cleanUrl: String = url.trim()
			cleanUrl = cleanUrl.removeSuffix("http://")
			cleanUrl = cleanUrl.removeSuffix("https://")
			cleanUrl = cleanUrl.removeSuffix("/")

			return cleanUrl
		}
	}
}

private data class PendingServerSetup(
	val url: String,
	val email: String,
	val password: String,
	val create: Boolean,
)

@Serializable
data object BackupManagerConfig
