package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.account.mfa.Totp
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.HttpClient
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SecurityPageTest : WebEndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"

	@Test
	fun `the security page offers to set up an authenticator app`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)

		val response = authed.get(route("dashboard/security"))

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "hx-post=\"/dashboard/security/totp/begin\"")
	}

	@Test
	fun `the dashboard links to the security page`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)

		assertContains(authed.get(route("dashboard")).bodyAsText(), "href=\"/dashboard/security\"")
	}

	@Test
	fun `begin returns a QR code and the manual key`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)

		val response = authed.hxPost("dashboard/security/totp/begin")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "<svg")
		assertContains(body, "otpauth://totp/")
		assertEquals(32, secretIn(body).length)
		assertFalse(hasConfirmedTotp())
	}

	@Test
	fun `confirm with a valid code shows eight recovery codes once`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val secret = secretIn(authed.hxPost("dashboard/security/totp/begin").bodyAsText())

		val response = authed.hxPost("dashboard/security/totp/confirm", "code=${codeFor(secret)}")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertEquals(8, recoveryCodesIn(body).size)
		assertContains(body, "download=")
		assertTrue(hasConfirmedTotp())
		assertFalse(authed.get(route("dashboard/security")).bodyAsText().contains(recoveryCodesIn(body).first()))
	}

	@Test
	fun `confirm with a wrong code is refused with an error toast`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val secret = secretIn(authed.hxPost("dashboard/security/totp/begin").bodyAsText())

		val response = authed.hxPost("dashboard/security/totp/confirm", "code=${wrongCodeFor(secret)}")

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertContains(response.bodyAsText(), "toast-error")
		assertFalse(hasConfirmedTotp())
	}

	@Test
	fun `disable needs the password as well as a code`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val secret = enroll(authed).first

		val wrongPassword = authed.hxPost(
			"dashboard/security/totp/disable",
			"password=wrong&code=${codeFor(secret, stepsAhead = 1)}",
		)
		assertEquals(HttpStatusCode.BadRequest, wrongPassword.status)
		assertTrue(hasConfirmedTotp())

		val response = authed.hxPost(
			"dashboard/security/totp/disable",
			"password=${encode(password)}&code=${encode(codeFor(secret, stepsAhead = 1).chunked(3).joinToString(" "))}",
		)
		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "hx-post=\"/dashboard/security/totp/begin\"")
		assertNull(totpRow())
	}

	@Test
	fun `disable accepts a recovery code`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val recoveryCode = enroll(authed).second.first()

		val response = authed.hxPost(
			"dashboard/security/totp/disable",
			"password=${encode(password)}&code=$recoveryCode",
		)

		assertEquals(HttpStatusCode.OK, response.status)
		assertNull(totpRow())
	}

	@Test
	fun `disable with a wrong code keeps the authenticator`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val secret = enroll(authed).first

		val response = authed.hxPost(
			"dashboard/security/totp/disable",
			"password=${encode(password)}&code=${wrongCodeFor(secret)}",
		)

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertTrue(hasConfirmedTotp())
	}

	@Test
	fun `regenerate needs the password and replaces the codes`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		val original = enroll(authed).second

		val refused = authed.hxPost("dashboard/security/recovery-codes/regenerate", "password=wrong")
		assertEquals(HttpStatusCode.BadRequest, refused.status)

		val response = authed.hxPost(
			"dashboard/security/recovery-codes/regenerate",
			"password=${encode(password)}",
		)
		assertEquals(HttpStatusCode.OK, response.status)
		val fresh = recoveryCodesIn(response.bodyAsText())
		assertEquals(8, fresh.size)
		assertNotEquals(original.toSet(), fresh.toSet())
	}

	@Test
	fun `the security page counts the unused recovery codes`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		val authed = login(email, password)
		enroll(authed)

		assertContains(authed.get(route("dashboard/security")).bodyAsText(), "8 of 8")
	}

	private suspend fun enroll(authed: HttpClient): Pair<String, List<String>> {
		val secret = secretIn(authed.hxPost("dashboard/security/totp/begin").bodyAsText())
		val body = authed.hxPost("dashboard/security/totp/confirm", "code=${codeFor(secret)}").bodyAsText()
		return secret to recoveryCodesIn(body)
	}

	private suspend fun HttpClient.hxPost(path: String, form: String = ""): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(form)
		}

	private fun totpRow() = database().serverDatabase.accountTotpQueries.getByUserId(userId()).executeAsOneOrNull()

	private fun hasConfirmedTotp() = totpRow()?.confirmed == true

	private fun userId() = database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

	companion object {
		fun secretIn(body: String): String =
			Regex("""id="totp-secret"[^>]*>([A-Z2-7 ]+)<""").find(body)!!.groupValues[1].replace(" ", "")

		fun recoveryCodesIn(body: String): List<String> =
			Regex("""class="recovery-codes__code">([a-z2-7]{5}-[a-z2-7]{5})<""").findAll(body).map { it.groupValues[1] }.toList()

		fun codeFor(secret: String, stepsAhead: Int = 0): String =
			Totp.code(Totp.base32Decode(secret), Totp.stepAt(Clock.System.now().epochSeconds) + stepsAhead)

		/** A code from far outside the drift window, so it is wrong for any current step. */
		fun wrongCodeFor(secret: String): String =
			Totp.code(Totp.base32Decode(secret), Totp.stepAt(Clock.System.now().epochSeconds) + 100)
	}
}
