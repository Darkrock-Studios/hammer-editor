package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.codeFor
import com.darkrockstudios.apps.hammer.e2e.SecurityPageTest.Companion.secretIn
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminResetMfaTest : WebEndToEndTest() {

	private val adminEmail = "admin@test.com"
	private val userEmail = "author@test.com"
	private val password = "password123!@#"

	@Test
	fun `the users list offers a reset only for accounts with MFA`(): Unit = runBlocking {
		startWithEnrolledUser()
		val admin = login(adminEmail, password)

		val fragment = admin.usersFragment()

		assertEquals(1, Regex("""hx-post="/admin/users/reset-mfa"""").findAll(fragment).count())
		assertContains(fragment, """{"userId": ${userId()}}""")
		assertContains(fragment, "hx-confirm=")
	}

	@Test
	fun `reset clears every factor and signs the account out of its apps`(): Unit = runBlocking {
		startWithEnrolledUser()
		val admin = login(adminEmail, password)
		assertTrue(tokenCount() > 0)

		val response = admin.hxPost("admin/users/reset-mfa", "userId=${userId()}")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "toast-success")
		assertNull(database().serverDatabase.accountTotpQueries.getByUserId(userId()).executeAsOneOrNull())
		assertEquals(0L, database().serverDatabase.accountRecoveryCodeQueries.countUnused(userId()).executeAsOne())
		assertEquals(0, tokenCount())
		assertFalse(admin.usersFragment().contains("/admin/users/reset-mfa"))
		// The password alone signs in again.
		login(userEmail, password)
	}

	@Test
	fun `a non-admin cannot reset MFA`(): Unit = runBlocking {
		startWithEnrolledUser()
		E2eTestData.createAccount(TestAccount("other@test.com", password), database())
		database().serverDatabase.whiteListQueries
			.addToWhiteList("other@test.com", kotlin.time.Clock.System.now(), "Test author", null)
		val other = login("other@test.com", password)

		val response = other.hxPost("admin/users/reset-mfa", "userId=${userId()}")

		assertNotEquals(HttpStatusCode.OK, response.status)
		assertTrue(database().serverDatabase.accountTotpQueries.getByUserId(userId()).executeAsOne().confirmed)
	}

	private suspend fun startWithEnrolledUser() {
		doStartServer()
		E2eTestData.createAccount(TestAccount(adminEmail, password, isAdmin = true), database())
		seedWhitelistedAccount(userEmail, password)
		val user = login(userEmail, password)
		val secret = secretIn(user.hxPost("dashboard/security/totp/begin").bodyAsText())
		user.hxPost("dashboard/security/totp/confirm", "code=${codeFor(secret)}")
	}

	private suspend fun HttpClient.usersFragment(): String =
		get(route("admin/users/user-fragment")) { header("HX-Request", "true") }.bodyAsText()

	private suspend fun HttpClient.hxPost(path: String, form: String = ""): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(form)
		}

	private fun userId() = database().serverDatabase.accountQueries.findAccount(userEmail).executeAsOne().id

	private fun tokenCount() =
		database().serverDatabase.authTokenQueries.getTokensByUserId(userId()).executeAsList().size
}
