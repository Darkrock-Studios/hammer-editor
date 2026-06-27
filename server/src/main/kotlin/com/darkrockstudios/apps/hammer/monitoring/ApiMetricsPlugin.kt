package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.koin.ktor.ext.inject
import kotlin.time.TimeSource

private val RequestStartKey = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("MonitoringRequestStart")

/**
 * Times every API request and feeds the result to the [MetricsCollector].
 *
 * The metric is keyed by the matched route TEMPLATE (e.g.
 * `/api/project/{userId}/{projectId}/upload_entity/{entityId}`) rather than the
 * concrete path, so per-user / per-project path parameters don't explode the
 * cardinality. The template is stashed on the call during routing by
 * [configureRouteTemplateCapture]; absence of one means no route matched, so
 * we skip recording rather than keying on the raw probe URL.
 *
 * Recording happens on [ResponseSent] rather than `RoutingCallFinished`: the
 * latter never fires when a handler throws, and the resulting status is only
 * mapped (e.g. to 500) by `StatusPages` after routing unwinds. Reading the
 * status here, once the response is fully sent, captures exception-mapped
 * errors that would otherwise be invisible to the error-rate metric.
 */
fun apiMetricsPlugin(collector: MetricsCollector) = createApplicationPlugin("ApiMetricsPlugin") {
	on(CallSetup) { call ->
		call.attributes.put(RequestStartKey, TimeSource.Monotonic.markNow())
	}
	on(ResponseSent) { call ->
		val start = call.attributes.getOrNull(RequestStartKey) ?: return@on
		val path = call.request.path()
		if (path != "/$API_ROUTE_PREFIX" && !path.startsWith("/$API_ROUTE_PREFIX/")) return@on
		val route = call.attributes.getOrNull(MatchedRouteTemplateKey) ?: return@on

		val durationMs = start.elapsedNow().inWholeMilliseconds
		val status = call.response.status()?.value ?: 0
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
