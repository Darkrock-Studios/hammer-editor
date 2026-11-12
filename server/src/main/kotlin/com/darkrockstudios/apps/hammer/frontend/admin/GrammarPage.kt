package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.respondHtmlFragment
import com.darkrockstudios.apps.hammer.frontend.utils.respondToast
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolDefaults
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolProxy
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.div
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.koin.ktor.ext.inject

private const val MAX_REQUESTS_PER_MINUTE = 10_000
private const val MAX_REQUEST_BYTES = 10_000_000

// GET /admin/grammar, POST /admin/grammar/settings, POST /admin/grammar/test
internal fun Route.adminGrammarPage(configRepository: ConfigRepository) {
	val proxy: LanguageToolProxy by application.inject()

	route("/grammar") {
		get {
			val config = proxy.config()
			val saved = configRepository.getStored(AdminServerConfig.LANGUAGETOOL_CONFIG) != null
			val model = mapOf(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeGrammar" to true,
				"grammarConfigured" to config.isConfigured,
				"grammarFromEnvironment" to (!saved && config.isConfigured),
				"grammarEnvVar" to LanguageToolDefaults.URL_ENV,
				"grammarUrl" to config.url,
				"grammarRequestsPerMinute" to config.requestsPerMinute,
				"grammarMaxRequestBytes" to config.maxRequestBytes,
				"grammarMaxRequestsPerMinute" to MAX_REQUESTS_PER_MINUTE,
				"grammarMinRequestBytes" to LanguageToolConfig.MIN_MAX_REQUEST_BYTES,
				"grammarMaxRequestBytesCeiling" to MAX_REQUEST_BYTES,
			)
			call.respond(MustacheContent("admin-grammar.mustache", call.withDefaults(model)))
		}

		hx.post("/settings") {
			val params = call.receiveParameters()
			val enteredUrl = params["url"]?.trim().orEmpty()
			val url = if (enteredUrl.isEmpty()) "" else LanguageToolConfig.validUrl(enteredUrl)
			if (url == null) {
				respondToast(call.msg("admin_grammar_url_invalid"), Toast.Error, HttpStatusCode.BadRequest)
				return@post
			}
			val current = proxy.config()
			proxy.saveConfig(
				LanguageToolConfig(
					url = url,
					requestsPerMinute = params["requestsPerMinute"]?.toIntOrNull()
						?.coerceIn(1, MAX_REQUESTS_PER_MINUTE) ?: current.requestsPerMinute,
					maxRequestBytes = params["maxRequestBytes"]?.toIntOrNull()
						?.coerceIn(LanguageToolConfig.MIN_MAX_REQUEST_BYTES, MAX_REQUEST_BYTES)
						?: current.maxRequestBytes,
				)
			)
			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		hx.post("/test") {
			val url = LanguageToolConfig.validUrl(call.receiveParameters()["url"].orEmpty())
			val count = url?.let { testConnection(proxy, LanguageToolConfig(url = it)) }
			val (style, text) = when {
				count == null -> "error-message" to call.msg("admin_grammar_url_invalid")
				count.isSuccess -> "success-message" to call.msg("admin_grammar_test_success", count.getOrThrow())
				else -> "error-message" to "${call.msg("admin_grammar_test_failed")}: ${count.exceptionOrNull()?.message}"
			}
			call.respondHtmlFragment { div(style) { +text } }
		}
	}
}

/** The number of languages the server at [config] offers. */
private suspend fun testConnection(proxy: LanguageToolProxy, config: LanguageToolConfig): Result<Int> =
	when (val result = proxy.languages(config)) {
		is LanguageToolProxy.Result.Failed -> Result.failure(IllegalStateException(result.reason))
		is LanguageToolProxy.Result.Reply -> if (result.status.isSuccess()) {
			runCatching { (Json.parseToJsonElement(result.body.decodeToString()) as JsonArray).size }
		} else {
			Result.failure(IllegalStateException(result.status.toString()))
		}
	}
