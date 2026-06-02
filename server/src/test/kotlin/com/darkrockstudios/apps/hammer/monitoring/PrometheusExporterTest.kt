package com.darkrockstudios.apps.hammer.monitoring

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PrometheusExporterTest {

	@Test
	fun `renders counters and a cumulative latency histogram`() {
		val endpoints = listOf(
			MergedEndpoint(
				route = "/api/x",
				method = "GET",
				requestCount = 5,
				errorCount = 1,
				totalDurationMs = 400,
				histogram = LatencyHistogram(le50 = 3, le100 = 1, le2500 = 1),
			),
		)

		val out = PrometheusExporter.render(endpoints)

		assertTrue(out.contains("# TYPE hammer_api_requests_total counter"))
		assertTrue(out.contains("""hammer_api_requests_total{route="/api/x",method="GET"} 5"""))
		assertTrue(out.contains("""hammer_api_request_errors_total{route="/api/x",method="GET"} 1"""))
		assertTrue(out.contains("# TYPE hammer_api_request_duration_milliseconds histogram"))

		// Cumulative buckets: 3, then +1 at 100, flat to 1000, +1 at 2500, +Inf = total 5.
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_bucket{route="/api/x",method="GET",le="50"} 3"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_bucket{route="/api/x",method="GET",le="100"} 4"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_bucket{route="/api/x",method="GET",le="1000"} 4"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_bucket{route="/api/x",method="GET",le="2500"} 5"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_bucket{route="/api/x",method="GET",le="+Inf"} 5"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_sum{route="/api/x",method="GET"} 400"""))
		assertTrue(out.contains("""hammer_api_request_duration_milliseconds_count{route="/api/x",method="GET"} 5"""))
	}
}
