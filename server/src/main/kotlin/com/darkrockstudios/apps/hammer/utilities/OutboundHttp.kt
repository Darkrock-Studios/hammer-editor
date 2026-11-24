package com.darkrockstudios.apps.hammer.utilities

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/** A client for requests the server makes to other sites. Non-2xx replies are returned, not thrown. */
fun outboundHttpClient(requestTimeoutMillis: Long, followRedirects: Boolean = false) = HttpClient(Java) {
	expectSuccess = false
	this.followRedirects = followRedirects
	install(HttpTimeout) {
		connectTimeoutMillis = 5_000
		this.requestTimeoutMillis = requestTimeoutMillis
	}
}

/** The body, or null when it is longer than [maxBytes]. Reads at most one byte past the limit. */
suspend fun HttpResponse.readCapped(maxBytes: Long): ByteArray? =
	bodyAsChannel().readBuffer(maxBytes + 1).readByteArray().takeIf { it.size <= maxBytes }
