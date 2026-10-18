package com.darkrockstudios.apps.hammer.common.data.account

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.MfaRequiredException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.server_setup_error_unknown
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*

class AccountReauthUseCase(
	private val globalSettingsStore: GlobalSettingsStore,
	private val accountApi: ServerAccountApi,
	private val httpClient: HttpClient,
) {
	private var pendingMfa: MfaChallenge? = null

	suspend fun reauthenticate(password: String): ReauthResult {
		pendingMfa = null
		val server = globalSettingsStore.serverSettings ?: error("No server info is present")
		val installId = globalSettingsStore.ensureInstallId()

		val result = accountApi.login(
			email = server.email,
			password = password,
			installId = installId,
		)

		return if (result.isSuccess) {
			storeSession(server, result.getOrThrow())
			ReauthResult.Success
		} else {
			when (val exception = result.exceptionOrNull()) {
				is MfaRequiredException -> {
					pendingMfa = exception.challenge
					ReauthResult.MfaRequired(exception.challenge)
				}

				else -> ReauthResult.Failure(displayMessage = displayMessage(exception), exception = exception)
			}
		}
	}

	/** Answers the challenge from [reauthenticate]. A refused code keeps the challenge open. */
	suspend fun completeMfa(code: String): ReauthResult {
		val challenge = pendingMfa ?: return ReauthResult.Failure(
			displayMessage = Res.string.server_setup_error_unknown.toMsg(),
			exception = IllegalStateException("No sign-in is waiting for a code"),
		)
		val server = globalSettingsStore.serverSettings ?: error("No server info is present")
		val installId = globalSettingsStore.ensureInstallId()

		val result = accountApi.loginMfa(
			mfaToken = challenge.token,
			code = code,
			installId = installId,
		)

		return if (result.isSuccess) {
			pendingMfa = null
			storeSession(server, result.getOrThrow())
			ReauthResult.Success
		} else {
			val exception = result.exceptionOrNull()
			val httpFailure = exception as? HttpFailureException
			if (httpFailure?.error?.errorCode == ApiErrorCode.MFA_CODE_INVALID) {
				ReauthResult.MfaRequired(challenge, error = httpFailure.error.displayMessage.toMsg())
			} else {
				pendingMfa = null
				ReauthResult.Failure(displayMessage = displayMessage(exception), exception = exception)
			}
		}
	}

	fun cancelMfa() {
		pendingMfa = null
	}

	private fun storeSession(server: ServerSettings, token: Token) {
		val bearerTokens = BearerTokens(accessToken = token.auth, refreshToken = token.refresh)
		httpClient.updateCredentials(bearerTokens)

		globalSettingsStore.updateServerSettings(
			server.copy(
				userId = token.userId,
				bearerToken = token.auth,
				refreshToken = token.refresh,
			)
		)
	}

	private fun displayMessage(exception: Throwable?): Msg =
		(exception as? HttpFailureException)?.error?.displayMessage?.toMsg()
			?: Res.string.server_setup_error_unknown.toMsg()
}

sealed interface ReauthResult {
	data object Success : ReauthResult
	/** [error] is set when a submitted code was refused and the challenge is still open. */
	data class MfaRequired(val challenge: MfaChallenge, val error: Msg? = null) : ReauthResult
	data class Failure(val displayMessage: Msg?, val exception: Throwable?) : ReauthResult
}
