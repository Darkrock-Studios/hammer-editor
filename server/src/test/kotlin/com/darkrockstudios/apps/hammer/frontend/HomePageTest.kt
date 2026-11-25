package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.HammerLinks
import com.darkrockstudios.apps.hammer.HomeConfig
import com.darkrockstudios.apps.hammer.HomeMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.CommunityAuthor
import com.darkrockstudios.apps.hammer.database.CommunityFeedStory
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
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
import kotlin.time.Instant

class HomePageTest : BaseTest() {

	private val configRepository: ConfigRepository = mockk()
	private val accountsRepository: AccountsRepository = mockk()
	private val projectAccessRepository: ProjectAccessRepository = mockk()

	private fun ApplicationTestBuilder.configureApp(
		feed: BlogFeed? = null,
		mode: HomeMode = HomeMode.PROMO,
		publicUrl: String? = null,
	) {
		val serverConfig = ServerConfig(
			communityEnabled = mode == HomeMode.COMMUNITY,
			home = HomeConfig(mode = mode),
			publicUrl = publicUrl,
		)
		val blogFeedRepository: BlogFeedRepository = mockk()
		every { blogFeedRepository.current() } returns feed
		application {
			setupKtorTestKoin(
				this@HomePageTest,
				module {
					single { configRepository }
					single { serverConfig }
				}
			)
			configureTemplating()
			configureLocalization()
			install(Sessions) {
				cookie<UserSession>(COOKIE_USER_SESSION)
			}
			routing {
				homePage(
					serverConfig, configRepository, MarkdownRenderer(), blogFeedRepository,
					accountsRepository, projectAccessRepository,
				)
			}
		}
	}

	private fun mockPageModelDependencies(serverMessage: String) {
		coEvery { configRepository.get(AdminServerConfig.SERVER_MESSAGE) } returns serverMessage
		coEvery { configRepository.get(AdminServerConfig.CONTACT_EMAIL) } returns ""
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns ""
		coEvery { configRepository.get(AdminServerConfig.DEFAULT_LOCALE) } returns "en"
		coEvery { configRepository.get(AdminServerConfig.SERVER_NAME) } returns ""
		coEvery { configRepository.get(AdminServerConfig.SERVER_TAGLINE) } returns ""
	}

	private fun mockCommunity(stories: List<CommunityFeedStory>, authors: List<CommunityAuthor>) {
		coEvery { projectAccessRepository.getCommunityFeedStories(any(), any()) } returns stories
		coEvery { accountsRepository.getNewestCommunityAuthors(any()) } returns authors
	}

	private val published = Instant.parse("2026-11-16T12:00:00Z")

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
	fun `GET lists the four newest posts outside the featured category`() = testApplication {
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
					post("Fifth", "fifth", "news"),
				),
			)
		)

		val body = getHome().bodyAsText()
		val latest = body.substringAfter("class=\"posts\"").substringBefore("</ul>")

		assertContains(latest, "href=\"https://blog.example.com/first\"")
		assertContains(latest, "Fourth")
		assertContains(latest, "Nov 16, 2026")
		assertFalse(latest.contains("Fifth"))
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

	@Test
	fun `community home shows the server name, tagline, stories, authors and About`() = testApplication {
		mockPageModelDependencies("")
		coEvery { configRepository.get(AdminServerConfig.SERVER_NAME) } returns "Thursday Night Writers"
		coEvery { configRepository.get(AdminServerConfig.SERVER_TAGLINE) } returns "We meet on Thursdays."
		coEvery { configRepository.get(AdminServerConfig.ABOUT_SERVER) } returns "We are **friendly**."
		mockCommunity(
			stories = listOf(CommunityFeedStory("uuid-1", "The Salt Road", "Mara Quill", published)),
			authors = listOf(CommunityAuthor(1, "Mara Quill", "Maps and footnotes.\nMore.", published)),
		)
		configureApp(mode = HomeMode.COMMUNITY)

		val body = getHome().bodyAsText()

		assertContains(body, "<title>Thursday Night Writers</title>")
		assertContains(body, "We meet on Thursdays.")
		assertContains(body, "The Salt Road")
		assertContains(body, "href=\"/a/Mara-Quill\"")
		assertContains(body, "Maps and footnotes.")
		assertFalse(body.contains("More."))
		assertContains(body, "All authors")
		assertContains(body, "<strong>friendly</strong>")
		assertFalse(body.contains("id=\"features\""))
		assertContains(body, "data-platform=\"linux\"")
	}

	@Test
	fun `community home leaves out the lists when nobody has joined`() = testApplication {
		mockPageModelDependencies("")
		mockCommunity(stories = emptyList(), authors = emptyList())
		configureApp(mode = HomeMode.COMMUNITY)

		val body = getHome().bodyAsText()

		assertFalse(body.contains("club-grid"))
		assertFalse(body.contains("club-about"))
		assertContains(body, "id=\"join\"")
	}

	@Test
	fun `a blank server name falls back to the public host`() = testApplication {
		mockPageModelDependencies("")
		configureApp(mode = HomeMode.MINIMAL, publicUrl = "https://sync.example.net")

		val body = getHome().bodyAsText()

		assertContains(body, "<title>sync.example.net</title>")
	}

	@Test
	fun `without a server name or public URL the title is Hammer, not the request host`() = testApplication {
		mockPageModelDependencies("")
		configureApp(mode = HomeMode.MINIMAL)

		val body = getHome().bodyAsText()

		assertContains(body, "<title>Hammer</title>")
		assertContains(body, "<meta name=\"description\" content=\"Hammer sync server\">")
	}

	@Test
	fun `community home invites anonymous visitors to join`() = testApplication {
		mockPageModelDependencies("")
		mockCommunity(stories = emptyList(), authors = emptyList())
		configureApp(mode = HomeMode.COMMUNITY)

		val body = getHome().bodyAsText()

		assertContains(body, "href=\"#join\"")
	}

	@Test
	fun `minimal home is kept out of search engines and offers login and download`() = testApplication {
		mockPageModelDependencies("")
		configureApp(mode = HomeMode.MINIMAL)

		val response = getHome()
		val body = response.bodyAsText()

		assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"])
		assertEquals(1, Regex("href=\"/login\"").findAll(body).count())
		assertContains(body, "data-platform=\"windows\"")
		assertFalse(body.contains("id=\"features\""))
		assertFalse(body.contains("class=\"masthead"))
	}

	@Test
	fun `promo home is indexable`() = testApplication {
		mockPageModelDependencies("")
		configureApp()

		assertEquals(null, getHome().headers["X-Robots-Tag"])
	}
}
