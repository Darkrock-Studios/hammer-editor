package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

private const val MAX_STACK_CHARS = 8000

/**
 * Record an unhandled error into the monitoring error log, keyed by the matched
 * route template and the authenticated user (when available). No-op unless
 * error tracking is currently enabled (read from the cached [MonitoringState],
 * so this stays off the database on the request path).
 */
suspend fun recordMonitoredError(
	call: ApplicationCall,
	cause: Throwable,
	errorRepository: ErrorRepository,
	monitoringState: MonitoringState,
) {
	if (!monitoringState.errorTrackingEnabled) return

	val route = (call as? RoutingCall)?.route?.toString() ?: call.request.path()
	errorRepository.record(
		exceptionType = cause::class.simpleName ?: "Throwable",
		route = route,
		userId = call.principal<ServerUserIdPrincipal>()?.id,
		message = cause.message,
		stackTrace = cause.stackTraceToString().take(MAX_STACK_CHARS),
	)
}
