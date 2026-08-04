package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupFlowTest : EndToEndTest() {

	private fun webClient() = HttpClient {
		install(HttpCookies)
		followRedirects = false
	}

	private suspend fun HttpClient.postSetupForm(
		email: String,
		password: String,
		confirmPassword: String,
	) = submitForm(
		url = route("setup"),
		formParameters = parameters {
			append("email", email)
			append("password", password)
			append("confirmPassword", confirmPassword)
		}
	)

	@Test
	fun `creating the first admin account from the setup page logs in and lands on the admin dashboard`() {
		doStartServer()

		runBlocking {
			webClient().use { web ->
				// With zero accounts the server is in setup mode: everything redirects to /setup
				val dashboard = web.get(route("dashboard"))
				assertEquals(HttpStatusCode.Found, dashboard.status)
				assertEquals("/setup", dashboard.headers["Location"])

				val response = web.postSetupForm("admin@test.com", "password123", "password123")
				assertEquals(HttpStatusCode.Found, response.status)
				assertEquals("/admin", response.headers["Location"])

				val account = database().serverDatabase.accountQueries
					.findAccount("admin@test.com").executeAsOne()
				assertTrue(account.is_admin)

				// The session cookie set by the POST authorizes the admin dashboard
				val admin = web.get(route("admin"))
				assertEquals(HttpStatusCode.OK, admin.status)
			}
		}
	}

	@Test
	fun `setup mode ends after the first account is created`() {
		doStartServer()

		runBlocking {
			webClient().use { web ->
				val created = web.postSetupForm("admin@test.com", "password123", "password123")
				assertEquals(HttpStatusCode.Found, created.status)

				val setupAgain = web.get(route("setup"))
				assertEquals(HttpStatusCode.Found, setupAgain.status)
				assertEquals("/", setupAgain.headers["Location"])

				// A stale or double-submitted form is bounced to login, not processed
				webClient().use { fresh ->
					val resubmit = fresh.postSetupForm("other@test.com", "password123", "password123")
					assertEquals(HttpStatusCode.Found, resubmit.status)
					assertEquals("/login", resubmit.headers["Location"])
				}
				assertNull(
					database().serverDatabase.accountQueries
						.findAccount("other@test.com").executeAsOneOrNull()
				)
			}
		}
	}

	@Test
	fun `mismatched passwords re-render the form with an error and create no account`() {
		doStartServer()

		runBlocking {
			webClient().use { web ->
				val response = web.postSetupForm("admin@test.com", "password123", "different456")

				assertEquals(HttpStatusCode.OK, response.status)
				assertContains(response.bodyAsText(), "Passwords do not match")
				assertNull(
					database().serverDatabase.accountQueries
						.findAccount("admin@test.com").executeAsOneOrNull()
				)
			}
		}
	}
}
