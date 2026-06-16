package com.darkrockstudios.apps.hammer.monitoring

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.assertEquals

class ApiMetricsPluginErrorTest {

	private val clock = object : Clock {
		override fun now(): Instant = Instant.parse("2026-01-15T12:34:56Z")
	}

	@Test
	fun `request that throws and is mapped to 500 by StatusPages is counted as an error`() = testApplication {
		val collector = MetricsCollector(clock)
		application {
			configureRouteTemplateCapture()
			install(StatusPages) {
				exception<Throwable> { call, _ ->
					call.respond(HttpStatusCode.InternalServerError)
				}
			}
			install(apiMetricsPlugin(collector))
			routing {
				route("api") {
					get("/boom") { throw RuntimeException("boom") }
				}
			}
		}

		client.get("/api/boom")

		val deltas = collector.drainToDeltas()
		assertEquals(1, deltas.size, "the request should have been recorded")
		assertEquals(1L, deltas.first().errorCount, "a 500 response must count as an error")
	}
}
