package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * htmx drops the body of a 4xx response, out-of-band toast and all, so an error toast only reaches
 * the user if the response opts back in with [SWAP_ERROR_HEADER]. The client-side handler in
 * toast.js keys off exactly what is asserted here.
 */
class ToastResponseTest {

	@Test
	fun `a toast-only error response is marked swappable`() = testApplication {
		application {
			routing {
				get("/error-toast") {
					respondToast("nope", Toast.Error, HttpStatusCode.BadRequest)
				}
			}
		}

		val response = client.get("/error-toast")

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		assertContains(response.bodyAsText(), "toast-error")
	}

	@Test
	fun `an error response with no content leaves the target alone`() = testApplication {
		application {
			routing {
				get("/error-toast") {
					respondHtmlWithToast(
						content = "",
						message = "nope",
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
				}
			}
		}

		val response = client.get("/error-toast")

		assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		assertEquals("none", response.headers["HX-Reswap"])
	}

	@Test
	fun `an error response carrying content still swaps into the target`() = testApplication {
		application {
			routing {
				get("/error-toast") {
					respondHtmlWithToast(
						content = "<p>replacement</p>",
						message = "nope",
						toast = Toast.Error,
						status = HttpStatusCode.BadRequest
					)
				}
			}
		}

		val response = client.get("/error-toast")

		assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		assertNull(response.headers["HX-Reswap"])
		assertContains(response.bodyAsText(), "<p>replacement</p>")
	}

	@Test
	fun `successful toast responses are left untouched`() = testApplication {
		application {
			routing {
				get("/ok-toast") {
					respondToast("saved", Toast.Success)
				}
				get("/ok-content") {
					respondHtmlWithToast(content = "", message = "saved")
				}
			}
		}

		listOf("/ok-toast", "/ok-content").forEach { path ->
			val response = client.get(path)

			assertEquals(HttpStatusCode.OK, response.status, path)
			assertNull(response.headers[SWAP_ERROR_HEADER], path)
			assertNull(response.headers["HX-Reswap"], path)
		}
	}

	@Test
	fun `rendered template responses carry the toast`() = testApplication {
		application {
			routing {
				get("/template") {
					respondTemplateWithToast(
						templatePath = "partials/server-notice.mustache",
						model = mapOf("hasInstanceNotice" to false),
						message = "saved"
					)
				}
			}
		}

		val response = client.get("/template")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "toast-success")
	}
}
