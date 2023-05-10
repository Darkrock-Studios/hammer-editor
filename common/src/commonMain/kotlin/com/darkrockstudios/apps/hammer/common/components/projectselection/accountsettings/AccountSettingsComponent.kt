package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.accountrepository.AccountRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.koin.core.component.inject

class AccountSettingsComponent(
	componentContext: ComponentContext,
	override val showProjectDirectory: Boolean,
) : AccountSettings, ComponentBase(componentContext) {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val exampleProjectRepository: ExampleProjectRepository by inject()
	private val accountRepository: AccountRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()

	private val _state = MutableValue(
		AccountSettings.State(
			projectsDir = projectsRepository.getProjectsDirectory(),
			uiTheme = globalSettingsRepository.globalSettings.uiTheme,
			syncAutomaticSync = globalSettingsRepository.globalSettings.automaticSyncing,
			syncAutoCloseDialog = globalSettingsRepository.globalSettings.autoCloseSyncDialog,
			syncAutomaticBackups = globalSettingsRepository.globalSettings.automaticBackups,
			maxBackups = globalSettingsRepository.globalSettings.maxBackups
		)
	)
	override val state: Value<AccountSettings.State> = _state

	init {
		watchSettingsUpdates()
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
							serverUrl = settings?.url
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
				serverSetup = true
			)
		}
	}

	override fun cancelServerSetup() {
		_state.getAndUpdate {
			it.copy(
				serverSetup = false
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

	override suspend fun setupServer(
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean
	): Result<Boolean> {
		if (state.value.serverError != null) {
			_state.getAndUpdate {
				it.copy(
					serverError = null
				)
			}
		}

		val cleanUrl = cleanUpUrl(url)
		return if (validateUrl(cleanUrl).not()) {
			val message = "Invalid URL"
			_state.getAndUpdate {
				it.copy(
					serverUrl = null,
					serverError = message
				)
			}
			Result.failure(Exception(message))
		} else {
			val result = accountRepository.setupServer(ssl, cleanUrl, email.trim(), password, create)
			if (result.isSuccess) {
				_state.getAndUpdate {
					it.copy(
						serverUrl = cleanUrl,
						serverSetup = false
					)
				}
			} else {
				_state.getAndUpdate {
					it.copy(
						serverUrl = null,
						serverError = result.exceptionOrNull()?.message ?: "Unknown error"
					)
				}
			}

			result
		}
	}

	companion object {
		// regex to validate url with port number
		private val urlWithPortRegex =
			Regex("""^(?:w{1,3}\.)?[^\s.]+(?:\.[a-z]+)*(?::\d+)?(?![^<]*(?:</\w+>|/?>))$""")
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