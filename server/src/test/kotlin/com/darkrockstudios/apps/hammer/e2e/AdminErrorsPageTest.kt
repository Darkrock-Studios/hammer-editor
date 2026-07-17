package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class AdminErrorsPageTest : EndToEndTest() {

	private val email = "admin@test.com"
	private val password = "password123!@#"

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val db = database().serverDatabase
		db.whiteListQueries.addToWhiteList(email, Clock.System.now(), "Test admin", null)

		val now = Clock.System.now()
		db.errorLogQueries.recordError(
			"UnsupportedProtocolVersionException|/api/.env|",
			"UnsupportedProtocolVersionException",
			"/api/.env",
			null,
			"Unsupported protocol version",
			null,
			426,
			now,
			now,
		)
		db.errorLogQueries.recordError(
			"RuntimeException|/api/sync|1",
			"RuntimeException",
			"/api/sync",
			1L,
			"boom",
			"at Foo.bar(Foo.kt:10)",
			500,
			now,
			now,
		)
	}

	private suspend fun login(): HttpClient {
		val authed = HttpClient {
			install(HttpCookies)
		}
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	@Test
	fun `ignoring an error type moves it into the ignored drawer and back`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			// Both groups render in the recent list; no ignored drawer yet.
			val before = authed.get(route("admin/monitoring/errors"))
			assertEquals(HttpStatusCode.OK, before.status)
			val beforeBody = before.bodyAsText()
			assertContains(beforeBody, "UnsupportedProtocolVersionException")
			assertContains(beforeBody, "RuntimeException")
			assertFalse(beforeBody.contains("mon-error--ignored"))
			assertFalse(beforeBody.contains("mon-rule-chip"))

			val ignore = authed.post(route("admin/monitoring/errors/ignore")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("type=UnsupportedProtocolVersionException")
			}
			assertEquals(HttpStatusCode.OK, ignore.status)
			assertEquals("true", ignore.headers["HX-Refresh"])

			// The type now renders as an ignored row plus a rule chip; the other group is untouched.
			val after = authed.get(route("admin/monitoring/errors")).bodyAsText()
			assertContains(after, "mon-error--ignored")
			assertContains(after, "mon-rule-chip")
			assertContains(after, "UnsupportedProtocolVersionException")
			assertContains(after, "RuntimeException")

			val unignore = authed.post(route("admin/monitoring/errors/unignore")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("type=UnsupportedProtocolVersionException")
			}
			assertEquals(HttpStatusCode.OK, unignore.status)

			val restored = authed.get(route("admin/monitoring/errors")).bodyAsText()
			assertFalse(restored.contains("mon-error--ignored"))
			assertFalse(restored.contains("mon-rule-chip"))
			assertContains(restored, "UnsupportedProtocolVersionException")
		}
	}
}
