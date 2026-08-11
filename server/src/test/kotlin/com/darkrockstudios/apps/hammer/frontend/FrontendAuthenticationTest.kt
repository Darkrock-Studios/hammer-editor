package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountNotFound
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class FrontendAuthenticationTest {

	private val accountRepo: AccountsRepository = mockk()
	private val whitelistRepo: WhiteListRepository = mockk()

	private fun account(
		userId: Long,
		email: String,
		isAdmin: Boolean = false,
		deletedAt: kotlin.time.Instant? = null,
	) = Account(
		id = userId,
		email = email,
		password_hash = "hash",
		cipher_secret = "secret",
		created = Clock.System.now(),
		is_admin = isAdmin,
		last_sync = Clock.System.now(),
		pen_name = null,
		bio = null,
		email_verified = true,
		community_member = false,
		deleted_at = deletedAt,
	)

	private fun ApplicationTestBuilder.configureApp() {
		application {
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION)
			}
			install(Authentication) {
				frontendAuthentication(accountRepo, whitelistRepo)
			}
			routing {
				post("/login") {
					call.sessions.set(
						UserSession(userId = 7, username = "user@example.com", isAdmin = false)
					)
					call.respondText("ok")
				}
				authenticate(SESSION_AUTH) {
					get("/protected") {
						val session = call.principal<UserSession>()
						call.respondText("user:${session?.userId}")
					}
				}
			}
		}
	}

	private suspend fun ApplicationTestBuilder.loginCookie(): Pair<String, String> {
		val noRedirect = createClient { followRedirects = false }
		val setCookie = noRedirect.post("/login").headers["Set-Cookie"]!!
		val cookie = parseServerSetCookieHeader(setCookie)
		return cookie.name to cookie.value
	}

	@Test
	fun `non-whitelisted user is challenged when whitelist enabled`() = testApplication {
		coEvery { accountRepo.getAccount(7) } returns account(7, "user@example.com")
		coEvery { whitelistRepo.isOnWhiteList("user@example.com") } returns false

		configureApp()
		val noRedirect = createClient { followRedirects = false }
		val (name, value) = loginCookie()

		val response = noRedirect.get("/protected") { cookie(name, value) }

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	@Test
	fun `whitelisted user reaches protected route`() = testApplication {
		coEvery { accountRepo.getAccount(7) } returns account(7, "user@example.com")
		coEvery { whitelistRepo.isOnWhiteList("user@example.com") } returns true

		configureApp()
		val noRedirect = createClient { followRedirects = false }
		val (name, value) = loginCookie()

		val response = noRedirect.get("/protected") { cookie(name, value) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("user:7", response.bodyAsText())
	}

	@Test
	fun `unknown user id is challenged not server error`() = testApplication {
		coEvery { accountRepo.getAccount(7) } throws AccountNotFound(7)

		configureApp()
		val noRedirect = createClient { followRedirects = false }
		val (name, value) = loginCookie()

		val response = noRedirect.get("/protected") { cookie(name, value) }

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	// The login page and the dashboard auth gate must agree on this predicate;
	// if they diverge, an unauthorized-but-logged-in user loops /login <-> /dashboard.
	@Test
	fun `session is unauthorized when not whitelisted`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } returns account(7, "user@example.com")
		coEvery { whitelistRepo.isOnWhiteList("user@example.com") } returns false

		val session = UserSession(userId = 7, username = "user@example.com", isAdmin = false)
		assertFalse(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}

	@Test
	fun `session is unauthorized when account is missing`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } throws AccountNotFound(7)

		val session = UserSession(userId = 7, username = "user@example.com", isAdmin = false)
		assertFalse(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}

	@Test
	fun `admin session bypasses the whitelist`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } returns account(7, "admin@example.com", isAdmin = true)
		coEvery { whitelistRepo.isOnWhiteList("admin@example.com") } returns false

		val session = UserSession(userId = 7, username = "admin@example.com", isAdmin = true)
		assertTrue(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}

	@Test
	fun `session is unauthorized when account is pending deletion`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } returns
			account(7, "user@example.com", deletedAt = Clock.System.now())

		val session = UserSession(userId = 7, username = "user@example.com", isAdmin = false)
		assertFalse(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}

	@Test
	fun `soft-deleted admin session is unauthorized despite admin bypass`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } returns
			account(7, "admin@example.com", isAdmin = true, deletedAt = Clock.System.now())

		val session = UserSession(userId = 7, username = "admin@example.com", isAdmin = true)
		assertFalse(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}

	@Test
	fun `session is authorized when whitelisted`() = runBlocking {
		coEvery { accountRepo.getAccount(7) } returns account(7, "user@example.com")
		coEvery { whitelistRepo.isOnWhiteList("user@example.com") } returns true

		val session = UserSession(userId = 7, username = "user@example.com", isAdmin = false)
		assertTrue(sessionIsAuthorized(session, accountRepo, whitelistRepo))
	}
}
