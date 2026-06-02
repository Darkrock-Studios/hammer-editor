package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.koin.ktor.ext.inject
import kotlin.time.TimeSource

private val RequestStartKey = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("MonitoringRequestStart")

/**
 * Times every API request and feeds the result to the [MetricsCollector].
 *
 * The metric is keyed by the matched route TEMPLATE (e.g.
 * `/api/project/{userId}/{projectName}/upload_entity`) rather than the concrete
 * path, so per-user / per-project path parameters don't explode the cardinality.
 * Falls back to the raw path only if the route can't be resolved.
 */
fun apiMetricsPlugin(collector: MetricsCollector) = createApplicationPlugin("ApiMetricsPlugin") {
	on(CallSetup) { call ->
		call.attributes.put(RequestStartKey, TimeSource.Monotonic.markNow())
	}
	on(ResponseSent) { call ->
		val start = call.attributes.getOrNull(RequestStartKey) ?: return@on
		val path = call.request.path()
		if (path != "/$API_ROUTE_PREFIX" && !path.startsWith("/$API_ROUTE_PREFIX/")) return@on

		val durationMs = start.elapsedNow().inWholeMilliseconds
		val status = call.response.status()?.value ?: 0
		val route = (call as? RoutingCall)?.route?.toString() ?: path
		val method = call.request.httpMethod.value

		collector.record(route = route, method = method, status = status, durationMs = durationMs)
	}
}

/** Installs the API metrics plugin, wiring it to the DI-provided collector. */
fun Application.configureApiMetrics() {
	val collector: MetricsCollector by inject()
	install(apiMetricsPlugin(collector))
	log.info("API metrics collection enabled")
}
