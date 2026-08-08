package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownServiceTest {

	private val markdownService = MarkdownService()

	@Test
	fun `markdownToSafeHtml converts basic markdown correctly`() {
		val markdown = "**bold** and *italic*"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<strong>bold</strong>"))
		assertTrue(result.contains("<em>italic</em>"))
	}

	@Test
	fun `markdownToSafeHtml renders strikethrough`() {
		val result = markdownService.markdownToSafeHtml("He was ~~dead~~ alive.")

		assertTrue(result.contains("<del>dead</del>"), result)
		assertFalse(result.contains("~~"), result)
	}

	@Test
	fun `markdownToSafeHtml renders strikethrough alongside other emphasis`() {
		val result = markdownService.markdownToSafeHtml("**bold** *italic* ~~struck~~")

		assertTrue(result.contains("<strong>bold</strong>"), result)
		assertTrue(result.contains("<em>italic</em>"), result)
		assertTrue(result.contains("<del>struck</del>"), result)
	}

	@Test
	fun `markdownToSafeHtml keeps strikethrough nested inside emphasis`() {
		val result = markdownService.markdownToSafeHtml("He said *hello ~~there~~* now.")

		assertTrue(result.contains("<del>there</del>"), result)
		assertFalse(result.contains("~~"), result)
	}

	@Test
	fun `markdownToSafeHtml does not let authored markup forge a del element`() {
		val result = markdownService.markdownToSafeHtml(
			"<span class=\"user-del\" onclick=\"alert('xss')\">x</span>"
		)

		assertFalse(result.contains("onclick"), result)
		assertFalse(result.contains("alert"), result)
	}

	@Test
	fun `markdownToSafeHtml renders headings`() {
		val markdown = "# Heading 1\n## Heading 2"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<h1>"))
		assertTrue(result.contains("<h2>"))
	}

	@Test
	fun `markdownToSafeHtml renders lists`() {
		val markdown = "- item 1\n- item 2"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<ul>"))
		assertTrue(result.contains("<li>"))
	}

	@Test
	fun `markdownToSafeHtml renders links with safe href`() {
		val markdown = "[link text](https://example.com)"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<a"))
		assertTrue(result.contains("href=\"https://example.com\""))
		assertTrue(result.contains("link text"))
	}

	@Test
	fun `markdownToSafeHtml strips script tags`() {
		val markdown = "<script>alert('xss')</script>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("<script>"))
		assertFalse(result.contains("alert"))
	}

	@Test
	fun `markdownToSafeHtml strips event handlers from img tags`() {
		val markdown = "<img src=x onerror=\"alert('xss')\">"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("onerror"))
		assertFalse(result.contains("alert"))
	}

	@Test
	fun `markdownToSafeHtml strips javascript URLs from links`() {
		val markdown = "[click me](javascript:alert('xss'))"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("javascript:"))
		assertFalse(result.contains("alert"))
	}

	@Test
	fun `markdownToSafeHtml strips inline event handlers`() {
		val markdown = "<div onclick=\"alert('xss')\">content</div>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("onclick"))
		assertFalse(result.contains("alert"))
	}

	@Test
	fun `markdownToSafeHtml strips iframe tags`() {
		val markdown = "<iframe src=\"https://evil.com\"></iframe>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("<iframe"))
	}

	@Test
	fun `markdownToSafeHtml strips object tags`() {
		val markdown = "<object data=\"malicious.swf\"></object>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("<object"))
	}

	@Test
	fun `markdownToSafeHtml strips embed tags`() {
		val markdown = "<embed src=\"malicious.swf\">"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("<embed"))
	}

	@Test
	fun `markdownToSafeHtml handles empty input`() {
		val result = markdownService.markdownToSafeHtml("")
		assertEquals("", result)
	}

	@Test
	fun `markdownToSafeHtml handles blank input`() {
		val result = markdownService.markdownToSafeHtml("   ")
		assertEquals("", result)
	}

	@Test
	fun `markdownToSafeHtml adds rel nofollow to links`() {
		val markdown = "[link](https://example.com)"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("rel=\"nofollow\""))
	}

	@Test
	fun `markdownToSafeHtml renders blockquotes`() {
		val markdown = "> This is a quote"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<blockquote>"))
	}

	@Test
	fun `markdownToSafeHtml renders code blocks`() {
		val markdown = "```\ncode here\n```"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertTrue(result.contains("<code>") || result.contains("<pre>"))
	}

	@Test
	fun `markdownToSafeHtml strips SVG with onload handler`() {
		val markdown = "<svg onload=\"alert('xss')\"></svg>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("onload"))
		assertFalse(result.contains("alert"))
		assertFalse(result.contains("<svg"))
	}

	@Test
	fun `markdownToSafeHtml strips data URLs`() {
		val markdown = "<a href=\"data:text/html,<script>alert('xss')</script>\">click</a>"
		val result = markdownService.markdownToSafeHtml(markdown)

		assertFalse(result.contains("data:"))
	}

	@Test
	fun `markdownToSafeHtml collapses extra blank lines unless asked to preserve them`() {
		val markdown = "First paragraph.\n\n\n\nSecond paragraph."
		val result = markdownService.markdownToSafeHtml(markdown)

		assertEquals(0, countBreaks(result))
		assertTrue(result.contains("First paragraph."))
		assertTrue(result.contains("Second paragraph."))
	}

	@Test
	fun `markdownToSafeHtml keeps a single blank line as a plain paragraph break`() {
		val markdown = "First paragraph.\n\nSecond paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertTrue(result.contains("First paragraph."))
		assertTrue(result.contains("Second paragraph."))
	}

	@Test
	fun `markdownToSafeHtml renders each extra blank line as a break`() {
		val markdown = "First paragraph.\n\n\n\nSecond paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(2, countBreaks(result))
		assertTrue(result.contains("First paragraph."))
		assertTrue(result.contains("Second paragraph."))
	}

	@Test
	fun `markdownToSafeHtml caps a runaway run of blank lines`() {
		val markdown = "First paragraph." + "\n".repeat(40) + "Second paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(MarkdownService.MAX_CONSECUTIVE_BREAKS, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml ignores blank lines before the first paragraph`() {
		val markdown = "\n\n\n\nFirst paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml ignores trailing blank lines`() {
		val markdown = "First paragraph." + "\n".repeat(6)
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml leaves blank lines inside fenced code untouched`() {
		val markdown = "```\nfirst\n\n\n\nsecond\n```"
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertTrue(result.contains("first"))
		assertTrue(result.contains("second"))
	}

	@Test
	fun `markdownToSafeHtml leaves blank lines inside an indented code block untouched`() {
		val markdown = "Intro:\n\n    code line one\n\n\n    code line two\n\nOutro."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertEquals(1, countTag(result, "pre"))
	}

	@Test
	fun `markdownToSafeHtml leaves a fence nested in a list item untouched`() {
		val markdown = "- item\n\n    ```\n    code\n\n\n    more\n    ```"
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertFalse(result.contains("&lt;br"))
		assertTrue(result.contains("code"))
		assertTrue(result.contains("more"))
	}

	@Test
	fun `markdownToSafeHtml does not close a fence on a different delimiter`() {
		val markdown = "```\nline one\n~~~\nline two\n\n\nline three\n```\n\nAfter."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertFalse(result.contains("&lt;br"))
		assertTrue(result.contains("After."))
	}

	@Test
	fun `markdownToSafeHtml does not close a fence on a shorter delimiter`() {
		val markdown = "````\nline one\n```\nline two\n\n\nline three\n````\n\nAfter."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertEquals(0, countBreaks(result))
		assertFalse(result.contains("&lt;br"))
		assertTrue(result.contains("After."))
	}

	@Test
	fun `markdownToSafeHtml keeps a list intact when extra blank lines follow it`() {
		val markdown = "- item 1\n- item 2\n\n\nAfter the list."
		val result = markdownService.markdownToSafeHtml(markdown, preserveBlankLines = true)

		assertTrue(result.contains("<ul>"))
		assertEquals(1, countBreaks(result))
		assertTrue(result.contains("After the list."))
	}

	private fun countBreaks(html: String) = Regex("<br\\s*/?>").findAll(html).count()

	private fun countTag(html: String, tag: String) = Regex("<$tag[\\s>]").findAll(html).count()
}
