package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSessionSigningTest {

	private val keyMaterial = "test-token-hmac-key-material".toByteArray(Charsets.UTF_8)

	private fun ApplicationTestBuilder.configureSessionApp() {
		application {
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION) {
					transform(userSessionTransformer(keyMaterial))
				}
			}
			install(Authentication) {
				session<UserSession>(SESSION_AUTH) {
					validate { session -> session }
					challenge { call.respondRedirect("/login") }
				}
			}
			routing {
				post("/login") {
					call.sessions.set(
						UserSession(
							userId = 7,
							username = "victim@example.com",
							isAdmin = true
						)
					)
					call.respondText("ok")
				}
				authenticate(SESSION_AUTH) {
					get("/protected") {
						val session = call.principal<UserSession>()
						call.respondText("user:${session?.userId}")
					}
				}
			}
		}
	}

	@Test
	fun `forged plaintext cookie is rejected`() = testApplication {
		configureSessionApp()
		val noRedirect = createClient { followRedirects = false }

		// The pre-fix format: an attacker who knows Ktor's plaintext session encoding
		// crafts a cookie naming an arbitrary admin userId.
		val forged = "userId=%23i999%23susername%23slong%23ahacker%40evil.com%23&isAdmin=%23bo%23t"
		val response = noRedirect.get("/protected") {
			cookie(COOKIE_USER_SESSION, forged)
		}

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	@Test
	fun `legitimately set cookie authenticates`() = testApplication {
		configureSessionApp()
		val noRedirect = createClient { followRedirects = false }

		val setCookie = noRedirect.post("/login").headers["Set-Cookie"]!!
		val sessionCookie = parseServerSetCookieHeader(setCookie)

		val response = noRedirect.get("/protected") {
			cookie(sessionCookie.name, sessionCookie.value)
		}

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("user:7", response.bodyAsText())
	}

	@Test
	fun `tampered cookie is rejected`() = testApplication {
		configureSessionApp()
		val noRedirect = createClient { followRedirects = false }

		val setCookie = noRedirect.post("/login").headers["Set-Cookie"]!!
		val sessionCookie: Cookie = parseServerSetCookieHeader(setCookie)
		val tampered = flipLastHexNibble(sessionCookie.value)
		assertTrue(tampered != sessionCookie.value, "Test setup failed to mutate the cookie")

		val response = noRedirect.get("/protected") {
			cookie(sessionCookie.name, tampered)
		}

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	@Test
	fun `cookie payload is not stored in cleartext`() = testApplication {
		configureSessionApp()
		val noRedirect = createClient { followRedirects = false }

		val setCookie = noRedirect.post("/login").headers["Set-Cookie"]!!
		val value = parseServerSetCookieHeader(setCookie).value

		assertFalse(value.contains("victim@example.com"), "Username leaked in plaintext")
		assertFalse(value.contains("isAdmin"), "Admin flag leaked in plaintext")
	}

	private fun flipLastHexNibble(value: String): String {
		val idx = value.indexOfLast { it in "0123456789abcdefABCDEF" }
		if (idx < 0) return value + "0"
		val c = value[idx]
		val replacement = if (c == '0') '1' else '0'
		return value.substring(0, idx) + replacement + value.substring(idx + 1)
	}
}
