package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.CreateAccountResult
import com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import com.darkrockstudios.apps.hammer.utils.testAccount
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SignupPageTest : BaseTest() {

	private val accountsComponent: AccountsComponent = mockk()
	private val accountsRepository: AccountsRepository = mockk()
	private val whiteListRepository: WhiteListRepository = mockk()
	private val termsOfServiceRepository: TermsOfServiceRepository = mockk()
	private val configRepository: ConfigRepository = mockk()
	private val securityRepository: SecurityRepository = mockk(relaxUnitFun = true)

	private fun ApplicationTestBuilder.configureApp() {
		application {
			setupKtorTestKoin(
				this@SignupPageTest,
				module {
					single { accountsRepository }
					single { configRepository }
				}
			)
			configureTemplating()
			configureLocalization()
			install(RateLimit) {
				register(RateLimitName(LOGIN_RATE_LIMIT)) {
					rateLimiter(limit = 1_000_000, refillPeriod = 1.seconds)
				}
			}
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION)
			}
			routing {
				signupPage(
					accountsComponent,
					accountsRepository,
					whiteListRepository,
					termsOfServiceRepository,
					configRepository,
					securityRepository,
				)
				post("/test-login") {
					call.sessions.set(
						UserSession(userId = 7, username = "user@example.com", isAdmin = false)
					)
					call.respondText("ok")
				}
			}
		}
	}

	private fun mockPageModelDependencies(contactEmail: String = "") {
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns ""
		coEvery { configRepository.get(AdminServerConfig.DEFAULT_LOCALE) } returns "en"
		coEvery { configRepository.get(AdminServerConfig.CONTACT_EMAIL) } returns contactEmail
		every { termsOfServiceRepository.challenge() } returns null
	}

	private suspend fun ApplicationTestBuilder.postSignupForm(
		email: String,
		password: String,
		confirmPassword: String,
		tosAccepted: Boolean = false,
		acceptedTosVersion: String? = null,
		acceptLanguage: String? = null,
	) = createClient { followRedirects = false }.submitForm(
		url = "/signup",
		formParameters = parameters {
			append("email", email)
			append("password", password)
			append("confirmPassword", confirmPassword)
			if (tosAccepted) append("tosAccepted", "true")
			if (acceptedTosVersion != null) append("acceptedTosVersion", acceptedTosVersion)
		}
	) {
		if (acceptLanguage != null) header(HttpHeaders.AcceptLanguage, acceptLanguage)
	}

	@Test
	fun `GET renders the signup form without a ToS block when no terms are configured`() = testApplication {
		mockPageModelDependencies()
		configureApp()

		val response = createClient { followRedirects = false }.get("/signup")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "action=\"/signup\"")
		assertContains(body, "name=\"email\"")
		assertContains(body, "name=\"confirmPassword\"")
		assertFalse(body.contains("tosAccepted"), "No ToS checkbox without configured terms")
	}

	@Test
	fun `GET renders the ToS checkbox and version when terms are configured`() = testApplication {
		mockPageModelDependencies()
		every { termsOfServiceRepository.challenge() } returns
			TermsOfServiceChallenge(text = "Be excellent", version = "v1")
		configureApp()

		val body = createClient { followRedirects = false }.get("/signup").bodyAsText()

		assertContains(body, "name=\"tosAccepted\"")
		assertContains(body, "name=\"acceptedTosVersion\" value=\"v1\"")
	}

	@Test
	fun `GET with an authorized session redirects to the dashboard`() = testApplication {
		coEvery { accountsRepository.getAccount(7) } returns testAccount()
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true
		configureApp()

		val client = createClient { followRedirects = false }
		val setCookie = client.post("/test-login").headers["Set-Cookie"]!!
		val cookie = parseServerSetCookieHeader(setCookie)

		val response = client.get("/signup") { cookie(cookie.name, cookie.value) }

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/dashboard", response.headers["Location"])
	}

	@Test
	fun `POST creates the account, records the attempt, sets a session, and redirects`() = testApplication {
		val token = Token(userId = 5, auth = "auth", refresh = "refresh")
		mockPageModelDependencies()
		coEvery {
			accountsComponent.createAccount("writer@test.com", "web", "password123", null)
		} returns CreateAccountResult.Success(token)
		coEvery { accountsRepository.isAdmin(5) } returns false
		configureApp()

		val response = postSignupForm("writer@test.com", "password123", "password123")

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/dashboard", response.headers["Location"])
		val setCookie = response.headers["Set-Cookie"]
		assertNotNull(setCookie)
		assertContains(setCookie, COOKIE_USER_SESSION)
		coVerify(exactly = 1) {
			securityRepository.recordLoginAttempt("writer@test.com", any(), true)
		}
	}

	@Test
	fun `POST with mismatched passwords re-renders and never reaches the component`() = testApplication {
		mockPageModelDependencies()
		configureApp()

		val response = postSignupForm("writer@test.com", "password123", "different456")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "Passwords do not match")
		assertContains(body, "value=\"writer@test.com\"")
		coVerify(exactly = 0) { accountsComponent.createAccount(any(), any(), any(), any()) }
		coVerify(exactly = 0) { securityRepository.recordLoginAttempt(any(), any(), any()) }
	}

	@Test
	fun `POST with empty fields re-renders with an error`() = testApplication {
		mockPageModelDependencies()
		configureApp()

		val response = postSignupForm("", "", "")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "Please fill in all fields")
		coVerify(exactly = 0) { accountsComponent.createAccount(any(), any(), any(), any()) }
	}

	@Test
	fun `POST for a not-allowed email re-renders the rejection and records a failed attempt`() = testApplication {
		mockPageModelDependencies()
		coEvery {
			accountsComponent.createAccount("stranger@test.com", "web", "password123", null)
		} returns CreateAccountResult.Failure(
			SResult.failure<Token>(
				"User not on whitelist",
				Msg.r("api_allowedusers_rejected"),
			)
		)
		coEvery { accountsComponent.checkIfWhiteListRejected("stranger@test.com") } returns true
		configureApp()

		val response = postSignupForm("stranger@test.com", "password123", "password123")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "not allowed on this server")
		coVerify(exactly = 1) {
			securityRepository.recordLoginAttempt("stranger@test.com", any(), false)
		}
	}

	@Test
	fun `POST for a not-allowed email falls back to English when the locale lacks the message`() = testApplication {
		mockPageModelDependencies()
		coEvery {
			accountsComponent.createAccount("stranger@test.com", "web", "password123", null)
		} returns CreateAccountResult.Failure(
			SResult.failure<Token>(
				"User not on whitelist",
				Msg.r("api_allowedusers_rejected"),
			)
		)
		coEvery { accountsComponent.checkIfWhiteListRejected("stranger@test.com") } returns true
		configureApp()

		// "xx" resolves to the deliberately incomplete test-resource bundle
		// (Messages_xx.properties); real locales are kept complete by Crowdin.
		val response = postSignupForm(
			"stranger@test.com", "password123", "password123",
			acceptLanguage = "xx",
		)

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "not allowed on this server")
	}

	@Test
	fun `POST with configured terms but an unchecked box is blocked before the component`() = testApplication {
		mockPageModelDependencies()
		every { termsOfServiceRepository.challenge() } returns
			TermsOfServiceChallenge(text = "Be excellent", version = "v1")
		configureApp()

		val response = postSignupForm("writer@test.com", "password123", "password123")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "must accept the Terms of Service")
		coVerify(exactly = 0) { accountsComponent.createAccount(any(), any(), any(), any()) }
	}

	@Test
	fun `POST with the box checked passes the accepted version to the component`() = testApplication {
		val token = Token(userId = 5, auth = "auth", refresh = "refresh")
		mockPageModelDependencies()
		every { termsOfServiceRepository.challenge() } returns
			TermsOfServiceChallenge(text = "Be excellent", version = "v1")
		coEvery {
			accountsComponent.createAccount("writer@test.com", "web", "password123", "v1")
		} returns CreateAccountResult.Success(token)
		coEvery { accountsRepository.isAdmin(5) } returns false
		configureApp()

		val response = postSignupForm(
			"writer@test.com", "password123", "password123",
			tosAccepted = true, acceptedTosVersion = "v1",
		)

		assertEquals(HttpStatusCode.Found, response.status)
		coVerify(exactly = 1) {
			accountsComponent.createAccount("writer@test.com", "web", "password123", "v1")
		}
	}

	@Test
	fun `POST that races a ToS change re-renders carrying the new version`() = testApplication {
		mockPageModelDependencies()
		every { termsOfServiceRepository.challenge() } returns
			TermsOfServiceChallenge(text = "Be excellent, v2 edition", version = "v2")
		coEvery {
			accountsComponent.createAccount("writer@test.com", "web", "password123", "v1")
		} returns CreateAccountResult.TermsRequired(
			TermsOfServiceChallenge(text = "Be excellent, v2 edition", version = "v2")
		)
		configureApp()

		val response = postSignupForm(
			"writer@test.com", "password123", "password123",
			tosAccepted = true, acceptedTosVersion = "v1",
		)

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "must accept the Terms of Service")
		assertContains(body, "name=\"acceptedTosVersion\" value=\"v2\"")
	}
}
