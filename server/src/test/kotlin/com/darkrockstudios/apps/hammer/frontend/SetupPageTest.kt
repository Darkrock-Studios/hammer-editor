package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SetupPageTest : BaseTest() {

	private val accountsRepository: AccountsRepository = mockk()
	private val projectsRepository: ProjectsRepository = mockk()
	private val configRepository: ConfigRepository = mockk()

	private fun ApplicationTestBuilder.configureApp() {
		application {
			setupKtorTestKoin(
				this@SetupPageTest,
				module {
					single { accountsRepository }
					single { projectsRepository }
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
				setupPage(ServerConfig())
			}
		}
	}

	private fun mockPageModelDependencies() {
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns ""
		coEvery { configRepository.get(AdminServerConfig.DEFAULT_LOCALE) } returns "en"
	}

	private suspend fun ApplicationTestBuilder.postSetupForm(
		email: String,
		password: String,
		confirmPassword: String,
	) = createClient { followRedirects = false }.submitForm(
		url = "/setup",
		formParameters = parameters {
			append("email", email)
			append("password", password)
			append("confirmPassword", confirmPassword)
		}
	)

	@Test
	fun `GET renders the admin account form while no users exist`() = testApplication {
		coEvery { accountsRepository.hasUsers() } returns false
		mockPageModelDependencies()
		configureApp()

		val response = createClient { followRedirects = false }.get("/setup")

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "action=\"/setup\"")
		assertContains(body, "name=\"email\"")
		assertContains(body, "name=\"confirmPassword\"")
	}

	@Test
	fun `GET redirects home once users exist`() = testApplication {
		coEvery { accountsRepository.hasUsers() } returns true
		configureApp()

		val response = createClient { followRedirects = false }.get("/setup")

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/", response.headers["Location"])
	}

	@Test
	fun `POST creates the admin account, sets a session, and redirects to the admin dashboard`() =
		testApplication {
			val token = Token(userId = 1, auth = "auth", refresh = "refresh")
			coEvery { accountsRepository.hasUsers() } returns false
			coEvery {
				accountsRepository.createAccount("admin@test.com", "web", "password123")
			} returns SResult.success(token)
			coEvery { projectsRepository.createUserData(1) } returns Unit
			coEvery { accountsRepository.isAdmin(1) } returns true
			configureApp()

			val response = postSetupForm("admin@test.com", "password123", "password123")

			assertEquals(HttpStatusCode.Found, response.status)
			assertEquals("/admin", response.headers["Location"])
			val setCookie = response.headers["Set-Cookie"]
			assertNotNull(setCookie)
			assertContains(setCookie, COOKIE_USER_SESSION)
			coVerify(exactly = 1) { accountsRepository.createAccount("admin@test.com", "web", "password123") }
			coVerify(exactly = 1) { projectsRepository.createUserData(1) }
		}

	@Test
	fun `POST with mismatched passwords re-renders with an error and never creates an account`() =
		testApplication {
			coEvery { accountsRepository.hasUsers() } returns false
			mockPageModelDependencies()
			configureApp()

			val response = postSetupForm("admin@test.com", "password123", "different456")

			assertEquals(HttpStatusCode.OK, response.status)
			val body = response.bodyAsText()
			assertContains(body, "Passwords do not match")
			assertContains(body, "value=\"admin@test.com\"")
			coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
		}

	@Test
	fun `POST with empty fields re-renders with an error`() = testApplication {
		coEvery { accountsRepository.hasUsers() } returns false
		mockPageModelDependencies()
		configureApp()

		val response = postSetupForm("", "", "")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "Please fill in all fields")
		coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
	}

	@Test
	fun `POST surfaces the repository's invalid email message and skips user data creation`() =
		testApplication {
			coEvery { accountsRepository.hasUsers() } returns false
			coEvery { accountsRepository.createAccount(any(), any(), any()) } returns SResult.failure(
				"invalid email",
				Msg.r("api_accounts_create_error_invalidemail"),
			)
			mockPageModelDependencies()
			configureApp()

			val response = postSetupForm("not-an-email", "password123", "password123")

			assertEquals(HttpStatusCode.OK, response.status)
			assertContains(response.bodyAsText(), "Invalid e-mail address")
			coVerify(exactly = 0) { projectsRepository.createUserData(any()) }
		}

	@Test
	fun `POST surfaces the repository's short password message`() = testApplication {
		coEvery { accountsRepository.hasUsers() } returns false
		coEvery { accountsRepository.createAccount(any(), any(), any()) } returns SResult.failure(
			"password failure",
			Msg.r("api_accounts_create_error_password_tooshort"),
		)
		mockPageModelDependencies()
		configureApp()

		val response = postSetupForm("admin@test.com", "short", "short")

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "Password too short")
	}

	@Test
	fun `POST after a user already exists redirects to login without creating anything`() =
		testApplication {
			coEvery { accountsRepository.hasUsers() } returns true
			configureApp()

			val response = postSetupForm("admin@test.com", "password123", "password123")

			assertEquals(HttpStatusCode.Found, response.status)
			assertEquals("/login", response.headers["Location"])
			assertNull(response.headers["Set-Cookie"])
			coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
		}

	@Test
	fun `POST that loses the creation race to another account does not grant an admin session`() =
		testApplication {
			val token = Token(userId = 2, auth = "auth", refresh = "refresh")
			coEvery { accountsRepository.hasUsers() } returns false
			coEvery {
				accountsRepository.createAccount("second@test.com", "web", "password123")
			} returns SResult.success(token)
			coEvery { projectsRepository.createUserData(2) } returns Unit
			coEvery { accountsRepository.isAdmin(2) } returns false
			configureApp()

			val response = postSetupForm("second@test.com", "password123", "password123")

			assertEquals(HttpStatusCode.Found, response.status)
			assertEquals("/dashboard", response.headers["Location"])
		}
}
