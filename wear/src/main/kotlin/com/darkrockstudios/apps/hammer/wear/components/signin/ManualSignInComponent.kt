package com.darkrockstudios.apps.hammer.wear.components.signin

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.isInsecureServerUrl
import com.darkrockstudios.apps.hammer.common.data.globalsettings.parseServerUrl
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.wear.data.LocalNetworkAccess
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManualSignInComponent(
	componentContext: ComponentContext,
	private val accountUseCase: AccountUseCase,
	private val localNetworkAccess: LocalNetworkAccess,
	private val strRes: StrRes,
) : ComponentBase(componentContext), ManualSignIn {

	private val _state = MutableValue(ManualSignIn.State())
	override val state: Value<ManualSignIn.State> = _state

	private var password: String = ""

	// The watch's RemoteInput keyboard capitalises the first letter and offers no way to turn that
	// off, so both of these are folded down to what the server actually matches on.
	override fun updateServer(server: String) {
		val trimmed = server.trim().lowercase()
		_state.update { it.copy(server = trimmed, serverInsecure = isInsecureServerUrl(trimmed), error = null) }
	}

	override fun updateEmail(email: String) {
		_state.update { it.copy(email = email.trim().lowercase(), error = null) }
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
			// The login is the first request, so a LAN server has to be reachable before it.
			if (isLocalNetworkBlocked(parsedUrl.host)) {
				withContext(dispatcherMain) {
					_state.update { it.copy(busy = false, localNetworkBlocked = true) }
				}
				return@launch
			}
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

	override fun onLocalNetworkPermissionResult() {
		_state.update { it.copy(localNetworkBlocked = false) }
		scope.launch {
			val host = parseServerUrl(_state.value.server).host
			if (isLocalNetworkBlocked(host)) {
				withContext(dispatcherMain) {
					_state.update { it.copy(error = ManualSignIn.SignInError.LocalNetworkDenied) }
				}
			} else {
				withContext(dispatcherMain) { signIn() }
			}
		}
	}

	/** An unreadable answer counts as reachable: the login then fails, or not, on its own terms. */
	private suspend fun isLocalNetworkBlocked(host: String): Boolean = try {
		localNetworkAccess.isBlocked(host)
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to check whether the server needs local network access", e)
		false
	}
}
