package com.darkrockstudios.apps.hammer.monitoring

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class ApiMetricsPluginTest {

	private val clock = object : Clock {
		override fun now(): Instant = Instant.parse("2026-01-15T12:00:00Z")
	}

	private fun collector() = MetricsCollector(clock).apply { setCollecting(true) }

	@Test
	fun `records the matched route template, not the concrete path`() = testApplication {
		val collector = collector()
		application {
			install(apiMetricsPlugin(collector))
			routing {
				route("api") {
					route("/project/{userId}/{projectName}") {
						post("/upload_entity/{entityId}") { call.respond(HttpStatusCode.OK) }
					}
				}
			}
		}

		client.post("/api/project/1/Alice%20In%20Wonderland/upload_entity/8")

		val recorded = collector.recentRequests().single()
		assertEquals("/api/project/{userId}/{projectName}/upload_entity/{entityId}", recorded.route)
		assertEquals("POST", recorded.method)
	}

	@Test
	fun `ignores non-api routes`() = testApplication {
		val collector = collector()
		application {
			install(apiMetricsPlugin(collector))
			routing {
				get("/admin/dashboard") { call.respond(HttpStatusCode.OK) }
			}
		}

		client.get("/admin/dashboard")

		assertEquals(0, collector.recentRequests().size)
	}
}
