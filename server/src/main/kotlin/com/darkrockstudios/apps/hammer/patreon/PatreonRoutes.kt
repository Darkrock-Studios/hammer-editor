package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.ServerConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.patreonRoutes(serverConfig: ServerConfig) {
	// Only set up routes if Patreon is enabled at server level
	if (serverConfig.patreonEnabled != true) {
		return
	}

	val webhookHandler: PatreonWebhookHandler by inject()

	route("/patreon") {
		webhookEndpoint(serverConfig, webhookHandler)
	}
}

private fun Route.webhookEndpoint(
	serverConfig: ServerConfig,
	webhookHandler: PatreonWebhookHandler
) {
	post("/webhook") {
		// Check if Patreon is enabled at server level
		if (serverConfig.patreonEnabled != true) {
			call.respond(HttpStatusCode.NotFound)
			return@post
		}

		val signature = call.request.headers["X-Patreon-Signature"]
		val eventType = call.request.headers["X-Patreon-Event"]
		val payload = call.receiveText()

		when (val result = webhookHandler.handleWebhook(payload, signature, eventType)) {
			is WebhookResult.Disabled -> {
				call.respond(HttpStatusCode.NotFound)
			}

			is WebhookResult.MissingSignature -> {
				call.respond(HttpStatusCode.BadRequest, "Missing signature")
			}

			is WebhookResult.NotConfigured -> {
				call.respond(HttpStatusCode.InternalServerError, "Webhook not configured")
			}

			is WebhookResult.InvalidSignature -> {
				call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
			}

			is WebhookResult.Success -> {
				call.respond(HttpStatusCode.OK, "Sync completed")
			}

			is WebhookResult.SyncFailed -> {
				call.respond(HttpStatusCode.InternalServerError, result.error)
			}
		}
	}
}
