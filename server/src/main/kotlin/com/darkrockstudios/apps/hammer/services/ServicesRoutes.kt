package com.darkrockstudios.apps.hammer.services

import com.darkrockstudios.apps.hammer.base.http.HammerService
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.SERVICES_ROUTE
import com.darkrockstudios.apps.hammer.base.http.ServicesResponse
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolProxy
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private const val ERR_KEY_UNAVAILABLE = "api_services_error_unavailable"
private const val ERR_KEY_RATE_LIMITED = "api_services_error_ratelimited"
private const val ERR_KEY_TOO_LARGE = "api_services_error_toolarge"
private const val ERR_KEY_CONTENT_TYPE = "api_services_error_contenttype"
private const val ERR_KEY_UPSTREAM = "api_services_error_upstream"

/** Statuses from the upstream that mean the request itself was wrong, so the client sees them as-is. */
private val PASSED_THROUGH = setOf(
	HttpStatusCode.BadRequest,
	HttpStatusCode.PayloadTooLarge,
	HttpStatusCode.TooManyRequests,
)

/** [languageToolEnabled] is the server config's switch: off, the LanguageTool routes are not installed. */
fun Route.servicesRoutes(languageToolEnabled: Boolean) {
	val languageTool: LanguageToolProxy by application.inject()

	authenticate(USER_AUTH) {
		route("/$SERVICES_ROUTE/{userId}") {
			get {
				val services = buildList {
					if (languageToolEnabled && languageTool.config().isConfigured) add(HammerService.LANGUAGETOOL)
				}
				call.respond(ServicesResponse(services))
			}
			if (languageToolEnabled) {
				route("/${HammerService.LANGUAGETOOL}/v2") {
					post("/check") { call.languageToolCheck(languageTool) }
					get("/languages") {
						val config = call.activeConfig(languageTool) ?: return@get
						if (!call.withinRateLimit(languageTool, config)) return@get
						call.respondUpstream(languageTool.languages(config))
					}
				}
			}
		}
	}
}

/** The config when an upstream is set; otherwise responds and returns null. */
private suspend fun ApplicationCall.activeConfig(proxy: LanguageToolProxy): LanguageToolConfig? {
	val config = proxy.config()
	if (!config.isConfigured) {
		respondError(HttpStatusCode.NotFound, ERR_KEY_UNAVAILABLE)
		return null
	}
	return config
}

/** Spends one of the caller's requests, or responds 429 and returns false. */
private suspend fun ApplicationCall.withinRateLimit(proxy: LanguageToolProxy, config: LanguageToolConfig): Boolean {
	val wait = proxy.acquire(principal<ServerUserIdPrincipal>()!!.id, config) ?: return true
	response.header(HttpHeaders.RetryAfter, ((wait.inWholeMilliseconds + 999) / 1000).coerceAtLeast(1).toString())
	respondError(HttpStatusCode.TooManyRequests, ERR_KEY_RATE_LIMITED)
	return false
}

private suspend fun ApplicationCall.languageToolCheck(proxy: LanguageToolProxy) {
	val config = activeConfig(proxy) ?: return
	if (!request.contentType().match(ContentType.Application.FormUrlEncoded)) {
		respondError(HttpStatusCode.UnsupportedMediaType, ERR_KEY_CONTENT_TYPE)
		return
	}
	val limit = config.maxRequestBytes.toLong()
	if ((request.contentLength() ?: 0) > limit) {
		respondError(HttpStatusCode.PayloadTooLarge, ERR_KEY_TOO_LARGE)
		return
	}
	val body = receiveChannel().readRemaining(limit + 1).readByteArray()
	if (body.size > limit) {
		respondError(HttpStatusCode.PayloadTooLarge, ERR_KEY_TOO_LARGE)
		return
	}
	if (!withinRateLimit(proxy, config)) return
	respondUpstream(proxy.check(config, parseQueryString(body.decodeToString())))
}

/**
 * Upstream failures become 502. Passing a 401 or 403 through would read as this server
 * refusing the user's token and set off the client's token refresh.
 */
private suspend fun ApplicationCall.respondUpstream(result: LanguageToolProxy.Result) {
	when {
		result is LanguageToolProxy.Result.Reply && (result.status.isSuccess() || result.status in PASSED_THROUGH) ->
			respondBytes(result.body, result.contentType, result.status)

		else -> respondError(HttpStatusCode.BadGateway, ERR_KEY_UPSTREAM)
	}
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, key: String) {
	respond(status, HttpResponseError(error = key, displayMessage = t(R(key))))
}
