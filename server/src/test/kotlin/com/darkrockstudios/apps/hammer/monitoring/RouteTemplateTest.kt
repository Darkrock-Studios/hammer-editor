package com.darkrockstudios.apps.hammer.monitoring

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RouteTemplateTest {

	@Test
	fun `status pages exception handler reads the cleaned route template after the handler throws`() = testApplication {
		var captured: String? = null
		application {
			configureRouteTemplateCapture()
			install(StatusPages) {
				exception<Throwable> { call, _ ->
					captured = call.attributes.getOrNull(MatchedRouteTemplateKey)
					call.respond(HttpStatusCode.InternalServerError)
				}
			}
			routing {
				route("api") {
					route("/project/{userId}/{projectName}") {
						post("/upload_entity/{entityId}") { throw RuntimeException("boom") }
					}
				}
			}
		}

		client.post("/api/project/1/Alice%20In%20Wonderland/upload_entity/8")

		assertEquals("/api/project/{userId}/{projectName}/upload_entity/{entityId}", captured)
	}

	@Test
	fun `strips authenticate and method selectors from the template`() = testApplication {
		var captured: String? = null
		application {
			install(Authentication) { basic("test") { validate { null } } }
			configureRouteTemplateCapture()
			routing {
				route("api") {
					authenticate("test", optional = true) {
						post("/account/{userId}/sync") {
							captured = call.attributes.getOrNull(MatchedRouteTemplateKey)
							call.respond(HttpStatusCode.OK)
						}
					}
				}
			}
		}

		client.post("/api/account/7/sync")

		assertEquals("/api/account/{userId}/sync", captured)
	}
}
