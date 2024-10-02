package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_CLIENT_VERSION
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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

		defaultRequest {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header(HEADER_CLIENT_VERSION, BuildMetadata.APP_VERSION)
		}

		install(ContentNegotiation) {
			json()
		}

		installCompression()

		install(HttpRequestRetry) {
			retryOnException(2)
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