package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import okio.IOException

fun createHttpClient(
    globalSettingsRepository: GlobalSettingsRepository,
): HttpClient {
    val tokenRefreshClient = createRefreshClient()
    val client = HttpClient(getHttpPlatformEngine()) {

        install(Logging) {
            logger = NapierHttpLogger()
            level = LogLevel.HEADERS
        }

        install(ContentNegotiation) {
            json()
        }

        installCompression()

        install(Auth) {
            bearer {
                realm = AUTH_REALM
                loadTokens {
                    loadTokens(globalSettingsRepository)
                }
                refreshTokens {
                    refreshToken(globalSettingsRepository, tokenRefreshClient)
                }
            }
        }
    }

    return client
}

private fun loadTokens(globalSettingsRepository: GlobalSettingsRepository): BearerTokens? {
    val accessToken = globalSettingsRepository.serverSettings?.bearerToken
    val refreshToken = globalSettingsRepository.serverSettings?.refreshToken
    Napier.d { "loadTokens" }
    return if (accessToken != null && refreshToken != null) {
        BearerTokens(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    } else {
        null
    }
}

private fun createRefreshClient(): HttpClient {
    return HttpClient(getHttpPlatformEngine()) {
        install(Logging) {
            logger = NapierHttpLogger()
            level = LogLevel.HEADERS
        }

        install(ContentNegotiation) {
            json()
        }
    }
}

private suspend fun refreshToken(
    globalSettingsRepository: GlobalSettingsRepository,
    client: HttpClient
): BearerTokens? {

    val refreshToken = globalSettingsRepository.serverSettings?.refreshToken
    val baseUrl =
        globalSettingsRepository.serverSettings?.url ?: throw IllegalStateException("No server URL")
    return if (refreshToken != null) {
        val result = refreshTokenRequest(
            httpClient = client,
            baseUrl = baseUrl,
            refreshToken = refreshToken,
            installId = "asd"
        )

        if (result.isSuccess) {
            val newTokens = result.getOrThrow()

            globalSettingsRepository.serverSettings?.let { oldSettings ->
                val newSettings = oldSettings.copy(
                    bearerToken = newTokens.auth,
                    refreshToken = newTokens.refresh
                )
                globalSettingsRepository.updateServerSettings(newSettings)
            }

            BearerTokens(
                accessToken = newTokens.auth,
                refreshToken = newTokens.refresh
            )
        } else {
            null
        }
    } else {
        null
    }
}

private suspend fun refreshTokenRequest(
    httpClient: HttpClient,
    baseUrl: String,
    refreshToken: String,
    installId: String,
): Result<Token> {
    return try {
        val response = httpClient.post("$baseUrl/account/refresh_token/") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("refreshToken", refreshToken)
                        append("installId", installId)
                    }
                )
            )
        }

        val token: Token = response.body()

        if (response.status.isSuccess()) {
            Result.success(token)
        } else {
            Result.failure(TokenRefreshFailed())
        }

    } catch (e: IOException) {
        Result.failure(TokenRefreshFailed())
    }
}

class TokenRefreshFailed : IllegalStateException()


private class NapierHttpLogger : Logger {
    override fun log(message: String) {
        Napier.i(tag = "Http", message = message)
    }
}

fun HttpClient.updateCredentials(credentials: BearerTokens) {
    val authPlugin = plugin(Auth)
    authPlugin.providers.removeAll { true }
    authPlugin.bearer {
        loadTokens {
            credentials
        }
    }
}

expect fun getHttpPlatformEngine(): HttpClientEngineFactory<*>

expect fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCompression()