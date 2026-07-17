package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.plugins.HttpStatusException
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.utils.io.ClosedByteChannelException

private const val MAX_STACK_CHARS = 8000

/**
 * True when the throwable is the client abandoning an in-flight response
 * (navigation away, tab close, HTTP/2 stream reset) rather than a server fault.
 * The engine surfaces the abort as a [ClosedByteChannelException] somewhere in
 * the cause chain when the response body write fails.
 */
fun Throwable.isClientAbort(): Boolean =
	generateSequence(this) { it.cause }.take(10).any { it is ClosedByteChannelException }

/**
 * The HTTP status an unhandled throwable should resolve to. Only exceptions that
 * deliberately carry a status ([HttpStatusException] — e.g. the protocol-version
 * rejection scanners trigger) map to 4xx; anything else is a genuine server
 * fault and stays 500 so real bugs aren't hidden as client errors.
 */
fun Throwable.toMonitoredStatus(): Int = when (this) {
	is HttpStatusException -> status.value
	else -> HttpStatusCode.InternalServerError.value
}

/**
 * Record an unhandled error into the monitoring error log, keyed by the matched
 * route template and the authenticated user (when available). No-op unless
 * error tracking is currently enabled (read from the cached [MonitoringState],
 * so this stays off the database on the request path).
 */
suspend fun recordMonitoredError(
	call: ApplicationCall,
	cause: Throwable,
	status: Int,
	errorRepository: ErrorRepository,
	monitoringState: MonitoringState,
) {
	if (!monitoringState.errorTrackingEnabled) return

	val route = call.attributes.getOrNull(MatchedRouteTemplateKey) ?: call.request.path()
	errorRepository.record(
		exceptionType = cause::class.simpleName ?: "Throwable",
		route = route,
		userId = call.principal<ServerUserIdPrincipal>()?.id,
		message = cause.message,
		stackTrace = cause.stackTraceToString().take(MAX_STACK_CHARS),
		status = status,
	)
}
