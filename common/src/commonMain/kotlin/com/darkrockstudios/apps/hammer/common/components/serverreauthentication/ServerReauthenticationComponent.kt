package com.darkrockstudios.apps.hammer.common.components.serverreauthentication

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.StrRes
import korlibs.io.async.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ServerReauthenticationComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissAuth: () -> Unit,
	private val onReauthSuccess: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ServerReauthentication {

	private val strRes: StrRes by inject()
	private val mainDispatcher by injectMainDispatcher()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val reauthUseCase: AccountReauthUseCase by inject()

	private var authenticateJob: Job? = null

	private val _state = MutableValue(
		ServerReauthentication.State(
			serverUrl = globalSettingsRepository.serverSettings?.url ?: "",
			serverEmail = globalSettingsRepository.serverSettings?.email ?: "",
		)
	)
	override val state: Value<ServerReauthentication.State> = _state

	override fun reauthenticate(password: String) {
		_state.getAndUpdate {
			it.copy(
				serverWorking = true
			)
		}

		authenticateJob?.cancel()

		authenticateJob = scope.launch {
			val authResult = reauthUseCase.reauthenticate(
				password = state.value.serverPassword,
			)

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						serverWorking = false
					)
				}

				if (isSuccess(authResult)) {
					onReauthSuccess()
				} else {
					_state.getAndUpdate {
						it.copy(
							serverError = authResult.displayMessage?.text(strRes)
						)
					}
				}
			}
		}
	}

	override fun cancelReauthentication() {
		dismissAuth()
	}

	override fun updateServerPassword(password: String) {
		_state.getAndUpdate {
			it.copy(serverPassword = password)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		authenticateJob?.cancel()
	}
}