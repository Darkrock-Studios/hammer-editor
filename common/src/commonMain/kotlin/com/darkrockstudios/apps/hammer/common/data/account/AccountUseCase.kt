package com.darkrockstudios.apps.hammer.common.data.account

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.MfaRequiredException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.TermsOfServiceRequiredException
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.server_setup_error_unknown
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*

class AccountUseCase(
	private val globalSettingsStore: GlobalSettingsStore,
	private val accountApi: ServerAccountApi,
	private val httpClient: HttpClient,
	private val strRes: StrRes,
) {
	/** The challenge a login is waiting on, with the settings the token belongs to. */
	private var pendingMfa: PendingMfa? = null

	suspend fun setupServer(
		url: String,
		email: String,
		password: String,
		create: Boolean,
		acceptedTosVersion: String? = null,
		ssl: Boolean = true,
	): ServerSetupResult {
		pendingMfa = null
		val installId = globalSettingsStore.ensureInstallId()
		val newSettings = ServerSettings(
			ssl = ssl,
			userId = -1,
			url = url,
			email = email,
			bearerToken = null,
			refreshToken = null,
		)

		globalSettingsStore.updateServerSettings(newSettings)

		val result = if (create) {
			accountApi.createAccount(
				email = email,
				password = password,
				installId = installId,
				acceptedTosVersion = acceptedTosVersion,
			)
		} else {
			accountApi.login(
				email = email,
				password = password,
				installId = installId,
			)
		}

		return if (result.isSuccess) {
			storeSession(newSettings, result.getOrThrow())
			ServerSetupResult.Success
		} else {
			when (val exception = result.exceptionOrNull()) {
				// Keep the provisional server settings so accepting the terms can retry the request.
				is TermsOfServiceRequiredException -> ServerSetupResult.TermsRequired(exception.challenge)
				is MfaRequiredException -> {
					pendingMfa = PendingMfa(exception.challenge, newSettings)
					ServerSetupResult.MfaRequired(exception.challenge)
				}

				else -> {
					globalSettingsStore.deleteServerSettings()
					ServerSetupResult.Failure(displayMessage = displayMessage(exception), exception = exception)
				}
			}
		}
	}

	/**
	 * Answers the challenge from [setupServer] with a TOTP or recovery code. A refused code keeps
	 * the challenge open; any other failure discards it along with the provisional settings.
	 */
	suspend fun completeMfa(code: String): ServerSetupResult {
		val pending = pendingMfa ?: return ServerSetupResult.Failure(
			displayMessage = strRes.get(Res.string.server_setup_error_unknown).toMsg(),
			exception = IllegalStateException("No sign-in is waiting for a code"),
		)
		val installId = globalSettingsStore.ensureInstallId()

		val result = accountApi.loginMfa(
			mfaToken = pending.challenge.token,
			code = code,
			installId = installId,
		)

		return if (result.isSuccess) {
			pendingMfa = null
			storeSession(pending.settings, result.getOrThrow())
			ServerSetupResult.Success
		} else {
			val exception = result.exceptionOrNull()
			val httpFailure = exception as? HttpFailureException
			if (httpFailure?.error?.errorCode == ApiErrorCode.MFA_CODE_INVALID) {
				ServerSetupResult.MfaRequired(pending.challenge, error = httpFailure.error.displayMessage.toMsg())
			} else {
				pendingMfa = null
				globalSettingsStore.deleteServerSettings()
				ServerSetupResult.Failure(displayMessage = displayMessage(exception), exception = exception)
			}
		}
	}

	/** Forgets a pending challenge; the caller decides what to do with the provisional settings. */
	fun cancelMfa() {
		pendingMfa = null
	}

	private fun storeSession(settings: ServerSettings, token: Token) {
		val authedSettings = settings.copy(
			userId = token.userId,
			bearerToken = token.auth,
			refreshToken = token.refresh
		)

		val bearerTokens = BearerTokens(accessToken = token.auth, refreshToken = token.refresh)
		httpClient.updateCredentials(bearerTokens)
		globalSettingsStore.updateServerSettings(authedSettings)
	}

	private suspend fun displayMessage(exception: Throwable?): Msg =
		(exception as? HttpFailureException)?.error?.displayMessage?.toMsg()
			?: strRes.get(Res.string.server_setup_error_unknown).toMsg()

	/**
	 * Mints a session for another of this user's installs. The settings returned belong to that
	 * install, so this install's credentials and stored settings are left untouched.
	 */
	suspend fun pairInstall(newInstallId: String): CResult<ServerSettings> {
		val current = globalSettingsStore.serverSettings
		if (current == null || current.userId < 0) {
			return CResult.failure(error = "No signed-in server to pair with")
		}

		val result = accountApi.pairInstall(newInstallId)
		return if (result.isSuccess) {
			val token = result.getOrThrow()
			CResult.success(
				current.copy(
					userId = token.userId,
					bearerToken = token.auth,
					refreshToken = token.refresh,
				)
			)
		} else {
			val exception = result.exceptionOrNull()
			CResult.failure(error = "Pairing failed", displayMessage = displayMessage(exception), exception = exception)
		}
	}

	/** Adopts a session another install minted for this one through [pairInstall]. */
	fun applyPairedSettings(settings: ServerSettings): CResult<Unit> {
		val bearerToken = settings.bearerToken
		if (bearerToken.isNullOrBlank() || settings.userId < 0) {
			return CResult.failure(error = "Paired settings carry no session")
		}

		httpClient.updateCredentials(BearerTokens(accessToken = bearerToken, refreshToken = settings.refreshToken))
		globalSettingsStore.updateServerSettings(settings)
		return CResult.success()
	}

	suspend fun testAuth(): Boolean {
		return accountApi.testAuth().isSuccess
	}

	private class PendingMfa(val challenge: MfaChallenge, val settings: ServerSettings)
}

sealed interface ServerSetupResult {
	data object Success : ServerSetupResult
	data class TermsRequired(val challenge: TermsOfServiceChallenge) : ServerSetupResult
	/** [error] is set when a submitted code was refused and the challenge is still open. */
	data class MfaRequired(val challenge: MfaChallenge, val error: Msg? = null) : ServerSetupResult
	data class Failure(val displayMessage: Msg?, val exception: Throwable?) : ServerSetupResult
}
