package com.darkrockstudios.apps.hammer.common.data.account

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
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
	suspend fun setupServer(
		url: String,
		email: String,
		password: String,
		create: Boolean,
		acceptedTosVersion: String? = null,
		ssl: Boolean = true,
	): ServerSetupResult {
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
			val token: Token = result.getOrThrow()

			val authedSettings = newSettings.copy(
				userId = token.userId,
				bearerToken = token.auth,
				refreshToken = token.refresh
			)

			val bearerTokens = BearerTokens(accessToken = token.auth, refreshToken = token.refresh)
			httpClient.updateCredentials(bearerTokens)
			globalSettingsStore.updateServerSettings(authedSettings)

			ServerSetupResult.Success
		} else {
			val exception = result.exceptionOrNull()
			if (exception is TermsOfServiceRequiredException) {
				// Keep the provisional server settings so accepting the terms can retry the request.
				ServerSetupResult.TermsRequired(exception.challenge)
			} else {
				globalSettingsStore.deleteServerSettings()

				val httpFailure = exception as? HttpFailureException
				val displayMessage = httpFailure?.error?.displayMessage?.toMsg()
					?: strRes.get(Res.string.server_setup_error_unknown).toMsg()

				ServerSetupResult.Failure(displayMessage = displayMessage, exception = exception)
			}
		}
	}

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
			val displayMessage = (exception as? HttpFailureException)?.error?.displayMessage?.toMsg()
				?: strRes.get(Res.string.server_setup_error_unknown).toMsg()
			CResult.failure(error = "Pairing failed", displayMessage = displayMessage, exception = exception)
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
}

sealed interface ServerSetupResult {
	data object Success : ServerSetupResult
	data class TermsRequired(val challenge: TermsOfServiceChallenge) : ServerSetupResult
	data class Failure(val displayMessage: Msg?, val exception: Throwable?) : ServerSetupResult
}