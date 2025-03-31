package com.darkrockstudios.apps.hammer.common.data.account

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*

class AccountReauthUseCase(
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val accountApi: ServerAccountApi,
	private val httpClient: HttpClient,
) {
	suspend fun reauthenticate(password: String): CResult<Unit> {
		val server = globalSettingsRepository.serverSettings ?: error("No server info is present")

		val result = accountApi.login(
			email = server.email,
			password = password,
			installId = server.installId
		)

		return if (result.isSuccess) {
			val token: Token = result.getOrThrow()

			val bearerTokens = BearerTokens(accessToken = token.auth, refreshToken = token.refresh)
			httpClient.updateCredentials(bearerTokens)

			val authedSettings = ServerSettings(
				userId = token.userId,
				ssl = server.ssl,
				url = server.url,
				email = server.email,
				installId = server.installId,
				bearerToken = token.auth,
				refreshToken = token.refresh,
			)
			globalSettingsRepository.updateServerSettings(authedSettings)

			CResult.success()
		} else {
			val message = (result.exceptionOrNull() as? HttpFailureException)?.error?.displayMessage ?: "Unknown error"
			CResult.failure(ServerSetupFailed(message))
		}
	}
}