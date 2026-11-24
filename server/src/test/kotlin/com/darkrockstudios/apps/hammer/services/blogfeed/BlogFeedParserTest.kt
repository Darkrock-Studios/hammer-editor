package com.darkrockstudios.apps.hammer.services.blogfeed

import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlogFeedParserTest {

	private fun feed(items: String) = """
		<?xml version="1.0" encoding="UTF-8"?>
		<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
			<channel>
				<title>The Blog</title>
				<link>https://example.com/blog/</link>
				<atom:link href="https://example.com/blog/feed.xml" rel="self" type="application/rss+xml"/>
				$items
			</channel>
		</rss>
	""".trimIndent()

	@Test
	fun `parses title, link, date and categories`() {
		val parsed = BlogFeedParser.parse(
			feed(
				"""
				<item>
					<title>Renaming Characters &amp; Places</title>
					<link>https://example.com/blog/rename</link>
					<pubDate>Sat, 07 Nov 2026 00:00:00 +0000</pubDate>
					<category>news</category>
					<category>development</category>
				</item>
				"""
			)
		)

		assertEquals("https://example.com/blog/", parsed.siteUrl)
		val post = parsed.posts.single()
		assertEquals("Renaming Characters & Places", post.title)
		assertEquals("https://example.com/blog/rename", post.url)
		assertEquals(OffsetDateTime.parse("2026-11-07T00:00:00Z"), post.published)
		assertEquals(setOf("news", "development"), post.categories)
		assertTrue(post.inCategory("News"))
	}

	@Test
	fun `keeps a post whose date can't be read`() {
		val parsed = BlogFeedParser.parse(
			feed("<item><title>Undated</title><link>https://example.com/a</link><pubDate>yesterday</pubDate></item>")
		)

		assertNull(parsed.posts.single().published)
	}

	@Test
	fun `skips items with no title or no http link`() {
		val parsed = BlogFeedParser.parse(
			feed(
				"""
				<item><link>https://example.com/untitled</link></item>
				<item><title>Script</title><link>javascript:alert(1)</link></item>
				<item><title>Relative</title><link>/blog/relative</link></item>
				<item><title>Kept</title><link>http://example.com/kept</link></item>
				"""
			)
		)

		assertEquals(listOf("Kept"), parsed.posts.map { it.title })
	}

	@Test
	fun `caps the number of posts`() {
		val items = (1..BlogFeedParser.MAX_POSTS + 5).joinToString("") {
			"<item><title>Post $it</title><link>https://example.com/$it</link></item>"
		}

		assertEquals(BlogFeedParser.MAX_POSTS, BlogFeedParser.parse(feed(items)).posts.size)
	}

	@Test
	fun `refuses a document that declares a doctype`() {
		val xml = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
			"<rss><channel><item><title>&x;</title><link>https://example.com/a</link></item></channel></rss>"

		assertFailsWith<Exception> { BlogFeedParser.parse(xml) }
	}

	@Test
	fun `refuses a document that is not RSS`() {
		assertFailsWith<IllegalArgumentException> { BlogFeedParser.parse("<html><body>Not found</body></html>") }
	}
}
