package com.darkrockstudios.apps.hammer.wear.components.signin

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.parseServerUrl
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManualSignInComponent(
	componentContext: ComponentContext,
	private val accountUseCase: AccountUseCase,
	private val strRes: StrRes,
) : ComponentBase(componentContext), ManualSignIn {

	private val _state = MutableValue(ManualSignIn.State())
	override val state: Value<ManualSignIn.State> = _state

	private var password: String = ""

	override fun updateServer(server: String) {
		_state.update { it.copy(server = server.trim(), error = null) }
	}

	override fun updateEmail(email: String) {
		_state.update { it.copy(email = email.trim(), error = null) }
	}

	override fun updatePassword(password: String) {
		this.password = password
		_state.update { it.copy(hasPassword = password.isNotEmpty(), error = null) }
	}

	override fun signIn() {
		val current = _state.value
		if (current.busy) return
		if (current.server.isBlank() || current.email.isBlank() || password.isEmpty()) {
			_state.update { it.copy(error = ManualSignIn.SignInError.MissingFields) }
			return
		}

		_state.update { it.copy(busy = true, error = null) }
		scope.launch {
			val parsedUrl = parseServerUrl(current.server)
			val result = accountUseCase.setupServer(
				url = parsedUrl.host,
				email = current.email,
				password = password,
				create = false,
				ssl = parsedUrl.ssl,
			)
			val error = when (result) {
				ServerSetupResult.Success -> null
				is ServerSetupResult.TermsRequired -> ManualSignIn.SignInError.TermsRequired
				is ServerSetupResult.Failure ->
					ManualSignIn.SignInError.Message(result.displayMessage?.text(strRes))
			}
			withContext(dispatcherMain) {
				if (error == null) password = ""
				_state.update { it.copy(busy = false, error = error) }
			}
		}
	}
}
