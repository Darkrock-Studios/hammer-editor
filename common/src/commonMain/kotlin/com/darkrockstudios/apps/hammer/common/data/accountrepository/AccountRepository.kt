package com.darkrockstudios.apps.hammer.common.data.accountrepository

import com.benasher44.uuid.uuid4
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*

class AccountRepository(
    private val globalSettingsRepository: GlobalSettingsRepository,
    private val accountApi: ServerAccountApi,
    private val httpClient: HttpClient
) {
    suspend fun setupServer(url: String, email: String, password: String, create: Boolean): Boolean {
        val newSettings = ServerSettings(
            email = email,
            url = url,
            deviceId = uuid4().toString(),
            bearerToken = null,
            refreshToken = null,
        )

        globalSettingsRepository.updateServerSettings(newSettings)

        val result = if (create) {
            accountApi.createAccount(
                email = email,
                password = password,
                deviceId = "asd"
            )
        } else {
            accountApi.login(
                email = email,
                password = password,
                deviceId = "asd"
            )
        }

        return if (result.isSuccess) {
            val token = result.getOrThrow()

            val authedSettings = newSettings.copy(
                bearerToken = token.auth,
                refreshToken = token.refresh
            )

            val bearerTokens = BearerTokens(accessToken = token.auth, refreshToken = token.refresh)
            httpClient.updateCredentials(bearerTokens)
            globalSettingsRepository.updateServerSettings(authedSettings)

            true
        } else {
            globalSettingsRepository.deleteServerSettings()

            false
        }
    }

    suspend fun testAuth() {
        accountApi.testAuth()
    }
}