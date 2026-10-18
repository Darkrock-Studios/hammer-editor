package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.account.mfa.Totp
import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.recoveryCodesIn
import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.secretIn
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LoginVerifyTest : WebEndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"
	private val testClock = TestClock(Clock.System)

	override val clock: Clock = testClock

	@Test
	fun `a correct password on an MFA account redirects to the verify page without a session`(): Unit = runBlocking {
		val secret = startWithMfa().first
		val browser = browser()

		val response = browser.postForm("login", "email=${encode(email)}&password=${encode(password)}")

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login/verify", response.headers["Location"])
		val cookies = browser.cookies(route("login/verify")).map { it.name }
		assertContains(cookies, "mfa_pending")
		assertFalse("user_session" in cookies)
		assertEquals(HttpStatusCode.Found, browser.get(route("dashboard")).status)
		assertTrue(recordedAttempts().isEmpty())
		assertTrue(secret.isNotEmpty())
	}

	@Test
	fun `the verify page asks for a code and offers a recovery code instead`(): Unit = runBlocking {
		startWithMfa()
		val browser = passwordStep()

		val page = browser.get(route("login/verify"))
		assertEquals(HttpStatusCode.OK, page.status)
		val body = page.bodyAsText()
		assertContains(body, "action=\"/login/verify\"")
		assertContains(body, "autocomplete=\"one-time-code\"")
		assertContains(body, "href=\"/login/verify?method=recovery\"")

		val recovery = browser.get(route("login/verify?method=recovery")).bodyAsText()
		assertContains(recovery, "href=\"/login/verify\"")
		assertFalse(recovery.contains("inputmode=\"numeric\""))
	}

	@Test
	fun `the verify page without a pending sign-in sends the visitor to login`(): Unit = runBlocking {
		startWithMfa()

		val response = browser().get(route("login/verify"))

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	@Test
	fun `a valid code signs in and is audited as a success`(): Unit = runBlocking {
		val secret = startWithMfa().first
		val browser = passwordStep()
		testClock.advanceTime(Totp.STEP_SECONDS.seconds)

		val response = browser.postForm("login/verify", "code=${currentCode(secret)}")

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/dashboard", response.headers["Location"])
		assertEquals(HttpStatusCode.OK, browser.get(route("dashboard")).status)
		assertPendingCleared(response)
		assertTrue(recordedAttempts().single().success)
	}

	@Test
	fun `a recovery code signs in once`(): Unit = runBlocking {
		val recoveryCode = startWithMfa().second.first()
		val first = passwordStep()

		assertEquals(HttpStatusCode.Found, first.postForm("login/verify", "code=$recoveryCode").status)

		val second = passwordStep()
		val reused = second.postForm("login/verify", "code=$recoveryCode")
		assertEquals(HttpStatusCode.OK, reused.status)
		assertEquals(HttpStatusCode.Found, second.get(route("dashboard")).status)
	}

	@Test
	fun `a wrong code shows the verify page again and is audited as a failure`(): Unit = runBlocking {
		val secret = startWithMfa().first
		val browser = passwordStep()

		val response = browser.postForm("login/verify", "code=${SecurityPageTest.wrongCodeFor(secret)}")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "action=\"/login/verify\"")
		assertContains(body, "That code didn")
		assertFalse(recordedAttempts().single().success)
		assertEquals(HttpStatusCode.Found, browser.get(route("dashboard")).status)
	}

	@Test
	fun `a locked account shows how long to wait`(): Unit = runBlocking {
		val secret = startWithMfa().first
		database().serverDatabase.accountTotpQueries.lock(until = testClock.now() + 15.minutes, userId = userId())
		val browser = passwordStep()
		testClock.advanceTime(Totp.STEP_SECONDS.seconds)

		val response = browser.postForm("login/verify", "code=${currentCode(secret)}")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "Wait 15 minutes")
		assertEquals(HttpStatusCode.Found, browser.get(route("dashboard")).status)
	}

	@Test
	fun `a pending sign-in older than five minutes is refused`(): Unit = runBlocking {
		val secret = startWithMfa().first
		val browser = passwordStep()
		testClock.advanceTime(5.minutes + 1.seconds)

		val response = browser.postForm("login/verify", "code=${currentCode(secret)}")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "action=\"/login\"")
		assertContains(body, "took too long")
		assertEquals(HttpStatusCode.Found, browser.get(route("dashboard")).status)
		assertNull(database().serverDatabase.accountTotpQueries.getByUserId(userId()).executeAsOne().locked_until)
	}

	@Test
	fun `a reset while the sign-in waits sends the user back to the password step`(): Unit = runBlocking {
		val secret = startWithMfa().first
		val browser = passwordStep()
		database().serverDatabase.accountTotpQueries.deleteAllForUser(userId())

		val response = browser.postForm("login/verify", "code=${currentCode(secret)}")

		assertContains(response.bodyAsText(), "action=\"/login\"")
		assertPendingCleared(response)
	}

	private fun assertPendingCleared(response: HttpResponse) {
		val cleared = response.headers.getAll("Set-Cookie").orEmpty().map(::parseServerSetCookieHeader)
			.single { it.name == "mfa_pending" }
		assertEquals(0, cleared.maxAge)
	}

	/** Seeds the account and turns on TOTP through the dashboard, then signs that session out. */
	private suspend fun startWithMfa(): Pair<String, List<String>> {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val secret = secretIn(authed.hxPost("dashboard/security/totp/begin").bodyAsText())
		val codes = recoveryCodesIn(authed.hxPost("dashboard/security/totp/confirm", "code=${currentCode(secret)}").bodyAsText())
		authed.get(route("logout"))
		database().serverDatabase.loginAttemptQueries.deleteAllForEmail(email)
		return secret to codes
	}

	private suspend fun passwordStep(): HttpClient = browser().also {
		val response = it.postForm("login", "email=${encode(email)}&password=${encode(password)}")
		check(response.status == HttpStatusCode.Found)
	}

	private fun browser() = HttpClient {
		install(HttpCookies)
		followRedirects = false
	}

	private suspend fun HttpClient.postForm(path: String, form: String): HttpResponse =
		post(route(path)) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(form)
		}

	private suspend fun HttpClient.hxPost(path: String, form: String = ""): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(form)
		}

	private fun currentCode(secret: String) =
		Totp.code(Totp.base32Decode(secret), Totp.stepAt(testClock.now().epochSeconds))

	private fun recordedAttempts() =
		database().serverDatabase.loginAttemptQueries.getRecentAttempts(10, 0).executeAsList().filter { it.email == email }

	private fun userId() = database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
