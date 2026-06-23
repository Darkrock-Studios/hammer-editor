package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.ServerConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import org.koin.ktor.ext.get

/**
 * The server's externally visible base URL for links delivered out-of-band (the
 * password-reset and review-invite emails). Resolved ONLY from the configured
 * [ServerConfig.publicUrl]; returns null when it is unset.
 *
 * The request's `Host` header is deliberately NOT used as a fallback: it is
 * client-controlled, so deriving an emailed link's host from it lets an attacker
 * point a victim's reset or invite link at a host they control. Operators that
 * send these emails must configure `publicUrl`.
 */
fun ApplicationCall.publicBaseUrl(): String? {
	val configured = try {
		application.get<ServerConfig>().publicUrl?.trim()?.trimEnd('/')
	} catch (_: Exception) {
		null
	}
	return configured?.takeIf { it.isNotEmpty() }
}

/**
 * The base URL derived from the current request's scheme/host/port. Safe ONLY for
 * links the requester will use within their own session (e.g. a "copy this link"
 * affordance shown to the signed-in author) — never for links emailed to a third
 * party, since the `Host` header is client-controlled. Use [publicBaseUrl] there.
 */
fun ApplicationCall.requestBaseUrl(): String {
	val scheme = request.origin.scheme
	val host = request.host()
	val port = request.port()
	return if (port == 80 || port == 443) {
		"$scheme://$host"
	} else {
		"$scheme://$host:$port"
	}
}
