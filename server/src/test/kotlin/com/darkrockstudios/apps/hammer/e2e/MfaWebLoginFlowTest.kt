package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.account.mfa.Totp
import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.recoveryCodesIn
import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.secretIn
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** The whole web journey: turn on an authenticator, sign out, and sign back in with a code from it. */
class MfaWebLoginFlowTest : WebEndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"
	private val testClock = TestClock(Clock.System)

	override val clock: Clock = testClock

	@Test
	fun `enroll, log out, and log back in with a generated code`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)

		val browser = HttpClient {
			install(HttpCookies)
			followRedirects = false
		}
		assertEquals(HttpStatusCode.Found, browser.postForm("login", credentials()).status)

		val secret = secretIn(browser.hxPost("dashboard/security/totp/begin").bodyAsText())
		val confirmed = browser.hxPost("dashboard/security/totp/confirm", "code=${code(secret)}")
		assertEquals(8, recoveryCodesIn(confirmed.bodyAsText()).size)

		browser.get(route("logout"))
		assertEquals(HttpStatusCode.Found, browser.get(route("dashboard")).status)

		val passwordStep = browser.postForm("login", credentials())
		assertEquals("/login/verify", passwordStep.headers["Location"])
		assertEquals(HttpStatusCode.OK, browser.get(route("login/verify")).status)

		testClock.advanceTime(Totp.STEP_SECONDS.seconds)
		val verified = browser.postForm("login/verify", "code=${code(secret)}")
		assertEquals("/dashboard", verified.headers["Location"])

		val dashboard = browser.get(route("dashboard"))
		assertEquals(HttpStatusCode.OK, dashboard.status)
		assertContains(dashboard.bodyAsText(), email)
	}

	private fun credentials() =
		"email=${URLEncoder.encode(email, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"

	private fun code(secret: String) = Totp.code(Totp.base32Decode(secret), Totp.stepAt(testClock.now().epochSeconds))

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
}
