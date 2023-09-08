package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.accountrepository.AccountRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.koin.core.component.inject

class AccountSettingsComponent(
	componentContext: ComponentContext,
	override val showProjectDirectory: Boolean,
) : AccountSettings,
	ComponentToaster by ComponentToasterImpl(),
	SavableComponent<AccountSettings.State>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()
	private val strRes: StrRes by inject()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val accountRepository: AccountRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()

	private var serverSetupJob: Job? = null

	private val _state by savableState {
		AccountSettings.State(
			projectsDir = projectsRepository.getProjectsDirectory(),
			uiTheme = globalSettingsRepository.globalSettings.uiTheme,
			syncAutomaticSync = globalSettingsRepository.globalSettings.automaticSyncing,
			syncAutoCloseDialog = globalSettingsRepository.globalSettings.autoCloseSyncDialog,
			syncAutomaticBackups = globalSettingsRepository.globalSettings.automaticBackups,
			maxBackups = globalSettingsRepository.globalSettings.maxBackups
		)
	}
	override val state: Value<AccountSettings.State> = _state

	init {
		watchSettingsUpdates()
	}

	private fun cancelSetupJob() {
		serverSetupJob?.cancel()
		serverSetupJob = null
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						val projectsPath = settings.projectsDirectory.toPath().toHPath()
						it.copy(
							projectsDir = projectsPath,
							uiTheme = settings.uiTheme
						)
					}
				}
			}
		}

		scope.launch {
			globalSettingsRepository.serverSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							currentSsl = settings?.ssl,
							currentUrl = settings?.url,
							currentEmail = settings?.email,
							serverIsLoggedIn = settings?.bearerToken?.isNotBlank() == true
						)
					}
				}
			}
		}
	}

	override fun setUiTheme(theme: UiTheme) {
		scope.launch {
			globalSettingsRepository.updateSettings {
				it.copy(
					uiTheme = theme
				)
			}
		}
	}

	override suspend fun reinstallExampleProject() {
		exampleProjectRepository.install()
	}

	override fun setProjectsDir(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)

		scope.launch {
			globalSettingsRepository.updateSettings {
				it.copy(
					projectsDirectory = path
				)
			}

			projectsRepository.ensureProjectDirectory()

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(projectsDir = hpath)
				}
			}
		}
	}

	override fun beginSetupServer() {
		_state.getAndUpdate {
			it.copy(
				serverSetup = true,
				serverUrl = it.currentUrl,
				serverEmail = it.currentEmail,
				serverSsl = it.currentSsl ?: true,
				serverPassword = null,
			)
		}
	}

	override fun cancelServerSetup() {
		cancelSetupJob()
		cleanUpServerSetup()
		_state.getAndUpdate {
			it.copy(
				serverSetup = false,
				serverError = null,
				serverWorking = false,
			)
		}
	}

	private fun cleanUpServerSetup() {
		_state.getAndUpdate {
			it.copy(
				serverSsl = true,
				serverUrl = null,
				serverEmail = null,
				serverPassword = null,
			)
		}
	}

	override suspend fun authTest(): Boolean {
		return accountRepository.testAuth()
	}

	override fun removeServer() {
		globalSettingsRepository.deleteServerSettings()
	}

	override suspend fun setAutomaticBackups(value: Boolean) {
		globalSettingsRepository.updateSettings {
			it.copy(
				automaticBackups = value
			)
		}
	}

	override suspend fun setAutoCloseDialogs(value: Boolean) {
		globalSettingsRepository.updateSettings {
			it.copy(
				autoCloseSyncDialog = value
			)
		}
	}

	override suspend fun setAutoSyncing(value: Boolean) {
		globalSettingsRepository.updateSettings {
			it.copy(
				automaticSyncing = value
			)
		}
	}

	override suspend fun setMaxBackups(value: Int) {
		globalSettingsRepository.updateSettings {
			it.copy(
				maxBackups = value
			)
		}
	}

	override fun reauthenticate() {
		_state.getAndUpdate {
			it.copy(
				serverSetup = true,
				serverSsl = state.value.currentSsl ?: true,
				serverUrl = state.value.currentUrl,
				serverEmail = state.value.currentEmail,
			)
		}
	}

	override fun updateServerUrl(url: String) {
		_state.getAndUpdate { it.copy(serverUrl = url) }
	}

	override fun updateServerSsl(ssl: Boolean) {
		_state.getAndUpdate { it.copy(serverSsl = ssl) }
	}

	override fun updateServerEmail(email: String) {
		_state.getAndUpdate { it.copy(serverEmail = email) }
	}

	override fun updateServerPassword(password: String) {
		_state.getAndUpdate { it.copy(serverPassword = password) }
	}

	override fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean,
		shouldRemoveLocalContent: Boolean
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

			val cleanUrl = cleanUpUrl(url)
			if (validateUrl(cleanUrl).not()) {
				val message = "Invalid URL"
				withContext(mainDispatcher) {
					_state.getAndUpdate {
						it.copy(
							serverError = message,
							serverWorking = false,
						)
					}
				}

				showToast(MR.strings.settings_server_setup_toast_failure, message)
			} else {
				if (shouldRemoveLocalContent) {
					removeLocalContent()
				}

				val result = accountRepository.setupServer(ssl, cleanUrl, email.trim(), password, create)
				withContext(mainDispatcher) {
					if (result.isSuccess) {
						cleanUpServerSetup()
						_state.getAndUpdate {
							it.copy(
								serverSetup = false,
								serverWorking = false,
							)
						}
						showToast(MR.strings.settings_server_setup_toast_success)
					} else {
						val message = result.exceptionOrNull()?.message
							?: strRes.get(MR.strings.settings_server_setup_toast_failure_unknown)
						_state.getAndUpdate {
							it.copy(
								serverError = message,
								serverWorking = false,
							)
						}
						showToast(scope, MR.strings.settings_server_setup_toast_failure, message)
					}
				}
			}
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