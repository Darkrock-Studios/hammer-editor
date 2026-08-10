package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.patreon.PatreonConfig
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomePageTest : BaseTest() {

	private val whiteListRepository: WhiteListRepository = mockk()
	private val configRepository: ConfigRepository = mockk()

	private fun ApplicationTestBuilder.configureApp() {
		application {
			setupKtorTestKoin(
				this@HomePageTest,
				module {
					single { configRepository }
				}
			)
			configureTemplating()
			configureLocalization()
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION)
			}
			routing {
				homePage(whiteListRepository, configRepository, ServerConfig(), MarkdownService())
			}
		}
	}

	private fun mockPageModelDependencies(serverMessage: String) {
		coEvery { configRepository.get(AdminServerConfig.SERVER_MESSAGE) } returns serverMessage
		coEvery { configRepository.get(AdminServerConfig.CONTACT_EMAIL) } returns ""
		coEvery { configRepository.get(AdminServerConfig.PATREON_CONFIG) } returns PatreonConfig()
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns ""
		coEvery { configRepository.get(AdminServerConfig.DEFAULT_LOCALE) } returns "en"
		coEvery { whiteListRepository.useWhiteList() } returns false
	}

	private suspend fun ApplicationTestBuilder.getHome() =
		createClient { followRedirects = false }.get("/")

	@Test
	fun `GET renders a link in the server message as an anchor`() = testApplication {
		mockPageModelDependencies("Read the [status page](https://status.example.com) before syncing.")
		configureApp()

		val response = getHome()

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertContains(body, "href=\"https://status.example.com\"")
		assertContains(body, "status page</a>")
		assertFalse(body.contains("[status page]"), "The link syntax is rendered, not shown literally")
	}

	@Test
	fun `GET renders emphasis in the server message`() = testApplication {
		mockPageModelDependencies("Syncing is **down** until _noon_.")
		configureApp()

		val body = getHome().bodyAsText()

		assertContains(body, "<strong>down</strong>")
		assertContains(body, "<em>noon</em>")
	}

	@Test
	fun `GET sanitizes markup authored in the server message`() = testApplication {
		mockPageModelDependencies("Careful <script>alert('xss')</script> and [x](javascript:alert(1))")
		configureApp()

		val body = getHome().bodyAsText()

		assertFalse(body.contains("alert"), "Script content never reaches the page")
		assertFalse(body.contains("javascript:"), "Unsafe link protocols are stripped")
	}

	@Test
	fun `GET without a server message renders no notice band`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		assertFalse(body.contains("instance-band"), "The band is absent when nothing is configured")
	}

	@Test
	fun `GET renders no notice band when the whole message sanitizes away`() = testApplication {
		mockPageModelDependencies("<script>alert('xss')</script>")
		configureApp()

		val body = getHome().bodyAsText()

		assertFalse(body.contains("instance-band"), "An empty band is worse than no band")
	}
}
