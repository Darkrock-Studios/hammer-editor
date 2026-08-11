package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock

class SignupFlowTest : WebEndToEndTest() {

	private fun webClient() = HttpClient {
		install(HttpCookies)
		followRedirects = false
	}

	private suspend fun HttpClient.postSignupForm(
		email: String,
		password: String,
		confirmPassword: String,
	) = submitForm(
		url = route("signup"),
		formParameters = parameters {
			append("email", email)
			append("password", password)
			append("confirmPassword", confirmPassword)
		}
	)

	@Test
	fun `an allowed email can self-register and lands on the dashboard`() {
		doStartServer()
		seedWhitelistedAccount("admin@test.com", "password123!@#")
		runBlocking {
			database().serverDatabase.whiteListQueries
				.addToWhiteList("newbie@test.com", Clock.System.now(), "Test invite", null)

			webClient().use { web ->
				val page = web.get(route("signup"))
				assertEquals(HttpStatusCode.OK, page.status)
				assertContains(page.bodyAsText(), "action=\"/signup\"")

				val response = web.postSignupForm("newbie@test.com", "password123!@#", "password123!@#")
				assertEquals(HttpStatusCode.Found, response.status)
				assertEquals("/dashboard", response.headers["Location"])

				val account = database().serverDatabase.accountQueries
					.findAccount("newbie@test.com").executeAsOne()
				assertFalse(account.is_admin, "A self-registered account must not be admin")

				// The session cookie set by the POST authorizes the dashboard
				val dashboard = web.get(route("dashboard"))
				assertEquals(HttpStatusCode.OK, dashboard.status)
			}
		}
	}

	@Test
	fun `a non-allowed email is rejected and no account is created`() {
		doStartServer()
		seedWhitelistedAccount("admin@test.com", "password123!@#")
		runBlocking {
			webClient().use { web ->
				val response = web.postSignupForm("stranger@test.com", "password123!@#", "password123!@#")

				assertEquals(HttpStatusCode.OK, response.status)
				assertContains(response.bodyAsText(), "not allowed on this server")
				assertNull(
					database().serverDatabase.accountQueries
						.findAccount("stranger@test.com").executeAsOneOrNull()
				)
			}
		}
	}
}
