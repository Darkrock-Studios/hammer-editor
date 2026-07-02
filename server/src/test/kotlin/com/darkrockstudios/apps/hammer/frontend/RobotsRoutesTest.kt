package com.darkrockstudios.apps.hammer.frontend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotsRoutesTest {

	@Test
	fun `disallows every functional path for the default agent`() {
		val body = buildRobotsTxt()
		val defaultGroup = body.substringBefore("# AI training")

		assertContains(defaultGroup, "User-agent: *")
		for (path in DISALLOWED_PATHS) {
			assertContains(defaultGroup, "Disallow: $path")
		}
		// Search engines may still crawl everything else (needed so public community
		// stories under /a/ can be indexed).
		assertContains(defaultGroup, "Allow: /")
	}

	@Test
	fun `blocks every known AI crawler with a full disallow`() {
		val body = buildRobotsTxt()
		val aiGroup = body.substringAfter("# AI training")

		for (agent in AI_CRAWLER_USER_AGENTS) {
			assertContains(aiGroup, "User-agent: $agent")
		}
		assertContains(aiGroup, "Disallow: /")
	}

	@Test
	fun `does not block general search crawlers`() {
		val body = buildRobotsTxt()
		assertFalse(body.contains("User-agent: Googlebot"), "Googlebot must stay allowed for search")
		assertFalse(body.contains("User-agent: Bingbot"), "Bingbot must stay allowed for search")
		// Link-preview fetchers must keep working so shared story links render cards.
		assertFalse(body.contains("facebookexternalhit"))
	}

	@Test
	fun `includes the AI training opt-out tokens for search engines that respect them`() {
		val body = buildRobotsTxt()
		assertContains(body, "User-agent: Google-Extended")
		assertContains(body, "User-agent: Applebot-Extended")
	}

	@Test
	fun `appends a sitemap line only when a url is provided`() {
		assertFalse(buildRobotsTxt().contains("Sitemap:"))
		assertContains(buildRobotsTxt("https://example.com/sitemap.xml"), "Sitemap: https://example.com/sitemap.xml")
	}

	@Test
	fun `serves robots txt as plain text`() = testApplication {
		application {
			routing { robotsRoutes() }
		}

		val response = client.get("/robots.txt")

		assertEquals(HttpStatusCode.OK, response.status)
		assertTrue(
			response.headers[HttpHeaders.ContentType]?.startsWith("text/plain") == true,
			"expected text/plain, got ${response.headers[HttpHeaders.ContentType]}",
		)
		val body = response.bodyAsText()
		assertTrue(body.startsWith("# Search engines"))
		assertContains(body, "User-agent: GPTBot")
	}
}
