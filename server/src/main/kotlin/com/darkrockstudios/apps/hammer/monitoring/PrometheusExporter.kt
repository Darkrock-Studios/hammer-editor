package com.darkrockstudios.apps.hammer.monitoring

/**
 * Renders collected API metrics in the Prometheus text exposition format
 * (version 0.0.4). Latency is exposed as a proper Prometheus histogram, built
 * from the additive latency bins (cumulative bucket counts + _sum + _count),
 * so Grafana/PromQL `histogram_quantile` works against it.
 *
 * Deliberately dependency-free (no Micrometer): it exposes the server's own
 * curated request metrics rather than generic JVM/Ktor metrics.
 */
object PrometheusExporter {

	/** Upper bounds (ms) matching LatencyHistogram bins, in order. */
	private val BOUNDS = listOf(50L, 100L, 250L, 500L, 1000L, 2500L)

	fun render(endpoints: List<MergedEndpoint>): String = buildString {
		appendLine("# HELP hammer_api_requests_total Total number of API requests.")
		appendLine("# TYPE hammer_api_requests_total counter")
		for (e in endpoints) {
			appendLine("hammer_api_requests_total${labels(e)} ${e.requestCount}")
		}

		appendLine("# HELP hammer_api_request_errors_total API requests that returned a 5xx response.")
		appendLine("# TYPE hammer_api_request_errors_total counter")
		for (e in endpoints) {
			appendLine("hammer_api_request_errors_total${labels(e)} ${e.errorCount}")
		}

		appendLine("# HELP hammer_api_request_duration_milliseconds API request latency.")
		appendLine("# TYPE hammer_api_request_duration_milliseconds histogram")
		for (e in endpoints) {
			val counts = listOf(
				e.histogram.le50, e.histogram.le100, e.histogram.le250,
				e.histogram.le500, e.histogram.le1000, e.histogram.le2500,
			)
			var cumulative = 0L
			for (i in BOUNDS.indices) {
				cumulative += counts[i]
				appendLine("hammer_api_request_duration_milliseconds_bucket${labels(e, "le" to BOUNDS[i].toString())} $cumulative")
			}
			val total = cumulative + e.histogram.leInf
			appendLine("hammer_api_request_duration_milliseconds_bucket${labels(e, "le" to "+Inf")} $total")
			appendLine("hammer_api_request_duration_milliseconds_sum${labels(e)} ${e.totalDurationMs}")
			appendLine("hammer_api_request_duration_milliseconds_count${labels(e)} $total")
		}
	}

	private fun labels(e: MergedEndpoint, vararg extra: Pair<String, String>): String {
		val all = listOf("route" to e.route, "method" to e.method) + extra
		return all.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
			"$k=\"${escape(v)}\""
		}
	}

	private fun escape(value: String): String = value
		.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
}
