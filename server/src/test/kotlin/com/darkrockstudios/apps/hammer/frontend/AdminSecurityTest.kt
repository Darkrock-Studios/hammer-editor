package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
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
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.time.Clock

class AdminSecurityTest : BaseTest() {

	private val accountRepo: AccountsRepository = mockk()
	private val whitelistRepo: WhiteListRepository = mockk()

	private fun account(admin: Boolean) = Account(
		id = 7,
		email = "user@example.com",
		password_hash = "hash",
		cipher_secret = "secret",
		created = Clock.System.now(),
		is_admin = admin,
		last_sync = Clock.System.now(),
		pen_name = null,
		bio = null,
		email_verified = true,
		community_member = false,
	)

	private fun ApplicationTestBuilder.configureApp() {
		application {
			setupKtorTestKoin(
				this@AdminSecurityTest,
				module {
					single { accountRepo }
					single { whitelistRepo }
				}
			)
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION)
			}
			install(Authentication) {
				frontendAuthentication(accountRepo, whitelistRepo)
			}
			routing {
				post("/login-admin") {
					call.sessions.set(UserSession(userId = 7, username = "user@example.com", isAdmin = true))
					call.respondText("ok")
				}
				adminOnly {
					get("/admin-area") { call.respondText("admin ok") }
				}
			}
		}
	}

	private suspend fun ApplicationTestBuilder.adminClaimingCookie(): Pair<String, String> {
		val noRedirect = createClient { followRedirects = false }
		val setCookie = noRedirect.post("/login-admin").headers["Set-Cookie"]!!
		val cookie = parseServerSetCookieHeader(setCookie)
		return cookie.name to cookie.value
	}

	@Test
	fun `cookie claiming admin is denied when the database says not admin`() = testApplication {
		coEvery { accountRepo.getAccount(7) } returns account(admin = false)
		coEvery { whitelistRepo.useWhiteList() } returns false
		coEvery { accountRepo.isAdmin(7) } returns false

		configureApp()
		val noRedirect = createClient { followRedirects = false }
		val (name, value) = adminClaimingCookie()

		val response = noRedirect.get("/admin-area") { cookie(name, value) }

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/unauthorized", response.headers["Location"])
	}

	@Test
	fun `admin per the database reaches the admin route`() = testApplication {
		coEvery { accountRepo.getAccount(7) } returns account(admin = true)
		coEvery { whitelistRepo.useWhiteList() } returns false
		coEvery { accountRepo.isAdmin(7) } returns true

		configureApp()
		val noRedirect = createClient { followRedirects = false }
		val (name, value) = adminClaimingCookie()

		val response = noRedirect.get("/admin-area") { cookie(name, value) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("admin ok", response.bodyAsText())
	}
}
