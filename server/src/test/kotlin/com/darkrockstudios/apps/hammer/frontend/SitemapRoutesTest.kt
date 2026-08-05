package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.database.CommunityAuthor
import com.darkrockstudios.apps.hammer.database.CommunityFeedStory
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class SitemapRoutesTest {

	private val published = Instant.parse("2026-01-02T03:04:05Z")

	@Test
	fun `the home page is always present`() {
		val locs = buildContentEntries(
			baseUrl = "https://hammer.example.com",
			includeAbout = false, includeTerms = false, includePrivacy = false,
			communityEnabled = false, authors = emptyList(), stories = emptyList(),
		).map { it.loc }
		assertEquals(listOf("https://hammer.example.com/"), locs)
	}

	@Test
	fun `static pages appear only when enabled and a trailing slash is trimmed`() {
		val locs = buildContentEntries(
			baseUrl = "https://x.test/",
			includeAbout = true, includeTerms = true, includePrivacy = true,
			communityEnabled = false, authors = emptyList(), stories = emptyList(),
		).map { it.loc }
		assertEquals(
			listOf("https://x.test/", "https://x.test/about", "https://x.test/terms", "https://x.test/privacy"),
			locs,
		)
	}

	@Test
	fun `community authors and stories map to their public urls with lastmod`() {
		val author = CommunityAuthor(id = 1, penName = "Jane Doe", bio = null, created = published)
		val story = CommunityFeedStory(
			projectUuid = "abc-123", projectName = "My Story", penName = "Jane Doe", publishedAt = published,
		)
		val entries = buildContentEntries(
			baseUrl = "https://x.test",
			includeAbout = false, includeTerms = false, includePrivacy = false,
			communityEnabled = true, authors = listOf(author), stories = listOf(story),
		)

		val authorUrl = "https://x.test/a/${ProjectName.penNameForUrl("Jane Doe")}"
		val storyUrl = "https://x.test/a/${ProjectName.penNameForUrl("Jane Doe")}/" +
			ProjectName.projectSegment("My Story", "abc-123")

		val locs = entries.map { it.loc }
		assertContains(locs, "https://x.test/community/feed")
		assertContains(locs, "https://x.test/community/authors")
		assertContains(locs, authorUrl)
		assertContains(locs, storyUrl)
		assertEquals(published.toString(), entries.first { it.loc == storyUrl }.lastmod)
	}

	@Test
	fun `community pages are omitted when community is disabled`() {
		val locs = buildContentEntries(
			baseUrl = "https://x.test",
			includeAbout = false, includeTerms = false, includePrivacy = false,
			communityEnabled = false, authors = emptyList(), stories = emptyList(),
		).map { it.loc }
		assertEquals(listOf("https://x.test/"), locs)
	}

	@Test
	fun `urlset escapes ampersands and emits lastmod`() {
		val xml = buildSitemapUrlsetXml(
			listOf(
				SitemapEntry("https://x.test/a?x=1&y=2"),
				SitemapEntry("https://x.test/p", "2026-01-02T03:04:05Z"),
			)
		)
		assertContains(xml, """<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
		assertContains(xml, "https://x.test/a?x=1&amp;y=2")
		assertContains(xml, "<lastmod>2026-01-02T03:04:05Z</lastmod>")
	}

	@Test
	fun `index lists the content sitemap and any extras`() {
		val xml = buildSitemapIndexXml(
			listOf("https://x.test/sitemap-content.xml", "https://x.test/extra/sitemap.xml")
		)
		assertContains(xml, "<sitemapindex")
		assertContains(xml, "<loc>https://x.test/sitemap-content.xml</loc>")
		assertContains(xml, "<loc>https://x.test/extra/sitemap.xml</loc>")
	}

	@Test
	fun `sitemap routes 404 when no public url is configured`() = testApplication {
		application {
			routing {
				sitemapRoutes(ServerConfig(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
			}
		}
		assertEquals(HttpStatusCode.NotFound, client.get("/sitemap.xml").status)
		assertEquals(HttpStatusCode.NotFound, client.get("/sitemap-content.xml").status)
	}
}
