package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.ServerConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import org.koin.ktor.ext.get

/**
 * The server's externally visible base URL, for links placed in emails.
 * Prefers the configured [ServerConfig.publicUrl] — required behind a reverse
 * proxy, where the request's scheme/host/port are the internal hop's (and the
 * Host header is client-controlled) — falling back to the request origin.
 */
fun ApplicationCall.publicBaseUrl(): String {
	val configured = try {
		application.get<ServerConfig>().publicUrl?.trim()?.trimEnd('/')
	} catch (_: Exception) {
		null
	}
	if (!configured.isNullOrEmpty()) return configured

	val scheme = request.origin.scheme
	val host = request.host()
	val port = request.port()
	return if (port == 80 || port == 443) {
		"$scheme://$host"
	} else {
		"$scheme://$host:$port"
	}
}
