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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Clock

class AdminHealthTokenPageTest : EndToEndTest() {

	private val email = "admin@test.com"
	private val password = "password123!@#"
	private val bearerPattern = Regex("Bearer ([A-Za-z0-9_-]+)")

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		database().serverDatabase.whiteListQueries.addToWhiteList(email, Clock.System.now(), "Test admin", null)
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
	fun `the jobs page lists each job's health path`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val page = authed.get(route("admin/monitoring/jobs"))

			assertEquals(HttpStatusCode.OK, page.status)
			val body = page.bodyAsText()
			assertContains(body, "/api/health/jobs/token-maintenance")
			assertContains(body, "/api/health/database")
			assertContains(body, "No health check token has been issued.")
		}
	}

	@Test
	fun `a generated token is shown once and unlocks the health detail until revoked`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val generated = authed.post(route("admin/monitoring/jobs/health-token")) {
				header("HX-Request", "true")
			}
			assertEquals(HttpStatusCode.OK, generated.status)
			val token = assertNotNull(bearerPattern.find(generated.bodyAsText())).groupValues[1]

			val page = authed.get(route("admin/monitoring/jobs")).bodyAsText()
			assertFalse(page.contains(token), "the token must not be shown again")
			assertContains(page, "Token issued")

			val database = client().get(route("api/health/database")) {
				header(HttpHeaders.Authorization, "Bearer $token")
			}
			assertEquals(HttpStatusCode.OK, database.status)

			val revoked = authed.post(route("admin/monitoring/jobs/health-token/revoke")) {
				header("HX-Request", "true")
			}
			assertEquals(HttpStatusCode.OK, revoked.status)
			assertContains(revoked.bodyAsText(), "No health check token has been issued.")

			val afterRevoke = client().get(route("api/health/database")) {
				header(HttpHeaders.Authorization, "Bearer $token")
			}
			assertEquals(HttpStatusCode.Unauthorized, afterRevoke.status)
		}
	}

	@Test
	fun `token routes are admin only`(): Unit = runBlocking {
		doStartServer()

		val response = client().post(route("admin/monitoring/jobs/health-token")) {
			header("HX-Request", "true")
		}

		assertFalse(response.status == HttpStatusCode.OK && bearerPattern.containsMatchIn(response.bodyAsText()))
	}
}
