package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
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
            level = LogLevel.INFO
        }

        install(ContentNegotiation) {
            json()
        }

        installCompression()

        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(3)
        }

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
    val serverSettings =
        globalSettingsRepository.serverSettings ?: throw IllegalStateException("No server URL")
    return if (refreshToken != null) {
        val result = refreshTokenRequest(
            httpClient = client,
            serverSettings = serverSettings,
            refreshToken = refreshToken,
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

fun HttpRequestBuilder.url(serverSettings: ServerSettings, path: String) {
    val serverHost: String
    val serverPort: Int?
    if (serverSettings.url.contains(":")) {
        serverHost = serverSettings.url.substringBefore(":")
        serverPort = serverSettings.url.substringAfter(":").toInt()
    } else {
        serverHost = serverSettings.url
        serverPort = null
    }

    url {
        protocol = if (serverSettings.ssl) URLProtocol.HTTPS else URLProtocol.HTTP
        host = serverHost
        if (serverPort != null) {
            port = serverPort
        }
        pathSegments = path.split("/")
    }
}

private suspend fun refreshTokenRequest(
    httpClient: HttpClient,
    serverSettings: ServerSettings,
    refreshToken: String,
): Result<Token> {
    return try {
        val response = httpClient.post {
            url(serverSettings, "/account/refresh_token/${serverSettings.userId}")
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("refreshToken", refreshToken)
                        append("installId", serverSettings.installId)
                    }
                )
            )
        }

        if (response.status.isSuccess()) {
            val token: Token = response.body()
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