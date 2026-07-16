package com.darkrockstudios.apps.hammer.common.data.account

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
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
		ssl: Boolean,
		url: String,
		email: String,
		password: String,
		create: Boolean,
		acceptedTosVersion: String? = null,
	): ServerSetupResult {
		val installId = globalSettingsStore.ensureInstallId()
		val newSettings = ServerSettings(
			userId = -1,
			ssl = ssl,
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

	suspend fun testAuth(): Boolean {
		return accountApi.testAuth().isSuccess
	}
}

sealed interface ServerSetupResult {
	data object Success : ServerSetupResult
	data class TermsRequired(val challenge: TermsOfServiceChallenge) : ServerSetupResult
	data class Failure(val displayMessage: Msg?, val exception: Throwable?) : ServerSetupResult
}