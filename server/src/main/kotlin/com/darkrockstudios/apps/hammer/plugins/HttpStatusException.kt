package com.darkrockstudios.apps.hammer.plugins

import io.ktor.http.HttpStatusCode

/**
 * An exception that carries the HTTP status it should resolve to. The global
 * error handler answers with [status] and the monitoring dashboard treats it as
 * that status, so deliberate client- and transport-fault conditions surface as
 * 4xx instead of being lumped in with genuine server faults (5xx).
 */
abstract class HttpStatusException(
	val status: HttpStatusCode,
	message: String,
) : Exception(message)

/** A request hit an `/api` route without a matching protocol version header. */
class UnsupportedProtocolVersionException(
	clientVersion: Int?,
	expectedVersion: Int,
) : HttpStatusException(
	HttpStatusCode.BadRequest,
	"Unsupported protocol version: $clientVersion (expected: $expectedVersion)",
)
