package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.HammerLinks
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.services.blogfeed.BlogFeed
import com.darkrockstudios.apps.hammer.services.blogfeed.BlogFeedRepository
import com.darkrockstudios.apps.hammer.services.blogfeed.BlogPost
import com.darkrockstudios.apps.hammer.utilities.MarkdownRenderer
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
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.time.OffsetDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomePageTest : BaseTest() {

	private val configRepository: ConfigRepository = mockk()

	private fun ApplicationTestBuilder.configureApp(feed: BlogFeed? = null) {
		val blogFeedRepository: BlogFeedRepository = mockk()
		every { blogFeedRepository.current() } returns feed
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
				homePage(configRepository, MarkdownRenderer(), blogFeedRepository)
			}
		}
	}

	private fun mockPageModelDependencies(serverMessage: String) {
		coEvery { configRepository.get(AdminServerConfig.SERVER_MESSAGE) } returns serverMessage
		coEvery { configRepository.get(AdminServerConfig.CONTACT_EMAIL) } returns ""
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns ""
		coEvery { configRepository.get(AdminServerConfig.DEFAULT_LOCALE) } returns "en"
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

	@Test
	fun `GET credits the masthead photograph in the footer`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		assertContains(body, "footer-credit")
		assertContains(body, "Maison de Balzac")
	}

	@Test
	fun `GET offers the iOS app through a smart app banner`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		assertContains(body, "<meta name=\"apple-itunes-app\" content=\"app-id=6770841038\">")
	}

	private fun post(title: String, slug: String, vararg categories: String) = BlogPost(
		title = title,
		url = "https://blog.example.com/$slug",
		published = OffsetDateTime.parse("2026-11-16T00:00:00Z"),
		categories = categories.toSet(),
	)

	@Test
	fun `GET lists the three newest posts outside the featured category`() = testApplication {
		mockPageModelDependencies("")
		configureApp(
			feed = BlogFeed(
				siteUrl = "https://blog.example.com/",
				posts = listOf(
					post("First", "first", "development"),
					post("A Principle", "principle", "principles"),
					post("Second", "second"),
					post("Third", "third", "news"),
					post("Fourth", "fourth", "news"),
				),
			)
		)

		val body = getHome().bodyAsText()
		val latest = body.substringAfter("class=\"posts\"").substringBefore("</ul>")

		assertContains(latest, "href=\"https://blog.example.com/first\"")
		assertContains(latest, "Third")
		assertContains(latest, "Nov 16, 2026")
		assertFalse(latest.contains("Fourth"))
		assertFalse(latest.contains("A Principle"))
		assertContains(body, "href=\"https://blog.example.com/\"")
	}

	@Test
	fun `GET links featured posts beside the principles, except the ones the cards already link`() = testApplication {
		mockPageModelDependencies("")
		configureApp(
			feed = BlogFeed(
				siteUrl = null,
				posts = listOf(
					post("On AI", "on-ai", "Principles"),
					BlogPost("Local First", HammerLinks.PRINCIPLE_LOCAL_FIRST, null, setOf("principles")),
				),
			)
		)

		val body = getHome().bodyAsText()
		val more = body.substringAfter("class=\"principles-more\"").substringBefore("</p>")

		assertContains(more, "href=\"https://blog.example.com/on-ai\"")
		assertFalse(more.contains("Local First"))
	}

	@Test
	fun `GET escapes post titles from the feed`() = testApplication {
		mockPageModelDependencies("")
		configureApp(feed = BlogFeed(null, listOf(post("<script>alert(1)</script>", "x"))))

		val body = getHome().bodyAsText()

		assertFalse(body.contains("<script>alert(1)"))
	}

	@Test
	fun `GET without a feed lists no posts but still links the tutorials`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		assertFalse(body.contains("class=\"posts\""))
		assertFalse(body.contains("principles-more"))
		assertContains(body, "class=\"video-card\" href=\"${HammerLinks.TUTORIALS_PLAYLIST.replace("=", "&#61;")}\"")
	}

	@Test
	fun `GET renders every feature tab with its panel`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		listOf("write", "world", "share", "sync").forEach { tab ->
			assertContains(body, "id=\"tab-$tab\" aria-controls=\"panel-$tab\"")
			assertContains(body, "id=\"panel-$tab\"")
		}
	}

	@Test
	fun `GET offers a download for every platform`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		val body = getHome().bodyAsText()

		listOf("windows", "macos", "linux", "android", "ios").forEach { platform ->
			assertContains(body, "data-platform=\"$platform\"")
		}
	}
}
