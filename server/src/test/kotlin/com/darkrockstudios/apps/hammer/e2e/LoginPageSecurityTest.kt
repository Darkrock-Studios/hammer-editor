package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The web login form feeds the same security audit trail as the API's
 * `/account/login`, and it audits what the server actually allowed: a sign-in the
 * whitelist turns away is a failure even though the password was right.
 */
class LoginPageSecurityTest : WebEndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"

	@Test
	fun `the web login form records both a successful and a failed attempt`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password, penName = "Test Author")

		login(email, password)
		val failed = postLogin(email, "wrong-password")

		assertEquals(HttpStatusCode.OK, failed.status, "A rejected sign-in re-renders the login form")
		assertContains(failed.bodyAsText(), "<form")

		val attempts = recordedAttemptsFor(email)
		assertEquals(2, attempts.size, "Both the successful and failed web sign-ins should be recorded")
		assertTrue(attempts.any { it.success })
		assertTrue(attempts.any { !it.success })
	}

	@Test
	fun `a whitelist rejection is recorded as a failed attempt`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password, penName = "Test Author")
		database().serverDatabase.whiteListQueries.removeFromWhiteList(email)

		val response = postLogin(email, password)

		assertEquals(HttpStatusCode.OK, response.status, "A whitelist rejection must not hand out a session")

		val attempts = recordedAttemptsFor(email)
		assertEquals(1, attempts.size)
		assertTrue(
			attempts.none { it.success },
			"Correct credentials the whitelist turned away are a denied sign-in, not a successful one",
		)
	}

	@Test
	fun `an attempt with no email is not recorded against an empty account`(): Unit = runBlocking {
		doStartServer()
		// Without an account the server is in setup mode and never reaches the login form.
		seedWhitelistedAccount(email, password)

		client().post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("password=${URLEncoder.encode("whatever", "UTF-8")}")
		}

		val attempt = database().serverDatabase.loginAttemptQueries
			.getRecentAttempts(10, 0)
			.executeAsList()
			.single()

		assertNull(
			attempt.email,
			"A blank email must be null so it stays out of the per-account brute-force queries",
		)
	}

	private suspend fun postLogin(email: String, password: String): HttpResponse =
		client().post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}

	private fun recordedAttemptsFor(email: String) =
		database().serverDatabase.loginAttemptQueries
			.getRecentAttempts(10, 0)
			.executeAsList()
			.filter { it.email == email }
}
