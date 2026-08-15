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
	fun `markdownToSafeHtml reflows single newlines unless asked to preserve them`() {
		val markdown = "First line.\nSecond line."
		val result = markdownService.markdownToSafeHtml(markdown)

		assertEquals(1, countTag(result, "p"))
		assertEquals(0, countBreaks(result))
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
	fun `markdownToSafeHtml gives every authored line its own paragraph`() {
		// An en dash: French dialogue opens with one, and unlike "- " it is not a list marker.
		val dash = "–"
		val markdown = "$dash Bonsoir madame.\n$dash Ne vous inquietez pas.\n$dash Tres bien."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(3, countTag(result, "p"))
		assertTrue(result.contains("<p>$dash Bonsoir madame.</p>"), result)
		assertTrue(result.contains("<p>$dash Ne vous inquietez pas.</p>"), result)
		assertTrue(result.contains("<p>$dash Tres bien.</p>"), result)
	}

	@Test
	fun `markdownToSafeHtml keeps emphasis whole when it spans an authored line break`() {
		val markdown = "He said *hello\nthere* now."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		// Splitting mid-emphasis would cross the tags; the line stays joined instead.
		assertEquals(1, countTag(result, "p"))
		assertTrue(result.contains("<em>hello"), result)
		assertFalse(result.contains("*"), result)
	}

	@Test
	fun `markdownToSafeHtml splits a line that is emphasized end to end`() {
		val markdown = "*First line.*\n*Second line.*"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(2, countTag(result, "p"))
		assertEquals(2, countTag(result, "em"))
	}

	@Test
	fun `markdownToSafeHtml renders a hard line break as a single line break`() {
		val markdown = "First line.  \nSecond line."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(2, countTag(result, "p"))
		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml renders a single blank line as a break`() {
		val markdown = "First paragraph.\n\nSecond paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(1, countBreaks(result))
		assertTrue(result.contains("First paragraph."))
		assertTrue(result.contains("Second paragraph."))
	}

	@Test
	fun `markdownToSafeHtml renders each blank line as a break`() {
		val markdown = "First paragraph.\n\n\n\nSecond paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(3, countBreaks(result))
		assertTrue(result.contains("First paragraph."))
		assertTrue(result.contains("Second paragraph."))
	}

	@Test
	fun `markdownToSafeHtml caps a runaway run of blank lines`() {
		val markdown = "First paragraph." + "\n".repeat(40) + "Second paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(MarkdownService.MAX_CONSECUTIVE_BREAKS, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml ignores blank lines before the first paragraph`() {
		val markdown = "\n\n\n\nFirst paragraph."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml ignores trailing blank lines`() {
		val markdown = "First paragraph." + "\n".repeat(6)
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml leaves blank lines around a heading to the heading's own spacing`() {
		val markdown = "## Chapter One\n\nFirst paragraph.\n\n\n## Chapter Two"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
		assertEquals(2, countTag(result, "h2"))
	}

	@Test
	fun `markdownToSafeHtml leaves lines and blank lines inside fenced code untouched`() {
		val markdown = "```\nfirst\n\n\n\nsecond\n```"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
		assertEquals(0, countTag(result, "p"))
		assertTrue(result.contains("first"))
		assertTrue(result.contains("second"))
	}

	@Test
	fun `markdownToSafeHtml leaves blank lines inside an indented code block untouched`() {
		val markdown = "Intro:\n\n    code line one\n\n\n    code line two\n\nOutro."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
		assertEquals(1, countTag(result, "pre"))
	}

	@Test
	fun `markdownToSafeHtml keeps a list intact`() {
		val markdown = "- item 1\n- item 2\n\n\nAfter the list."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(1, countTag(result, "ul"))
		assertEquals(2, countTag(result, "li"))
		assertEquals(0, countBreaks(result))
		assertTrue(result.contains("After the list."))
	}

	@Test
	fun `markdownToSafeHtml keeps a wrapped list item as one item`() {
		val markdown = "- item one\n  continued\n- item two"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(2, countTag(result, "li"))
		assertEquals(0, countTag(result, "p"))
	}

	@Test
	fun `markdownToSafeHtml gives a quoted line its own paragraph`() {
		val markdown = "> quote line one\n> quote line two"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(1, countTag(result, "blockquote"))
		assertEquals(2, countTag(result, "p"))
		assertEquals(0, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml renders a blank line inside a quote as a break`() {
		val markdown = "> quote line one\n>\n> quote line two"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(1, countTag(result, "blockquote"))
		assertEquals(2, countTag(result, "p"))
		assertEquals(1, countBreaks(result))
	}

	@Test
	fun `markdownToSafeHtml keeps a table intact`() {
		val markdown = "| a | b |\n| --- | --- |\n| 1 | 2 |\n\nAfter."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(0, countBreaks(result))
		assertEquals(1, countTag(result, "table"))
		assertEquals(2, countTag(result, "th"))
		assertEquals(2, countTag(result, "td"))
		assertTrue(result.contains("After."))
	}

	@Test
	fun `markdownToSafeHtml keeps a table's column alignment`() {
		val markdown = "| a | b | c |\n| :--- | :---: | ---: |\n| 1 | 2 | 3 |"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertTrue(result.contains("""align="center""""), result)
		assertTrue(result.contains("""align="right""""), result)
	}

	@Test
	fun `markdownToSafeHtml keeps the number an ordered list starts at`() {
		val markdown = "5. Fifth\n6. Sixth"
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertTrue(result.contains("""<ol start="5">"""), result)
	}

	@Test
	fun `markdownToSafeHtml lays out Windows line endings like any other`() {
		val unix = "First line.\nSecond line.\n\nAfter a blank line."
		val windows = unix.replace("\n", "\r\n")

		assertEquals(
			markdownService.markdownToSafeHtml(unix, preserveLineBreaks = true),
			markdownService.markdownToSafeHtml(windows, preserveLineBreaks = true),
		)
	}

	@Test
	fun `markdownToSafeHtml keeps blocks apart across Windows line endings`() {
		val markdown = "- one\r\n- two\r\n\r\nAfter the list."
		val result = markdownService.markdownToSafeHtml(markdown, preserveLineBreaks = true)

		assertEquals(2, countTag(result, "li"))
		assertTrue(result.contains("<p>After the list.</p>"), result)
	}

	private fun countBreaks(html: String) = Regex("<br\\s*/?>").findAll(html).count()

	private fun countTag(html: String, tag: String) = Regex("<$tag[\\s>]").findAll(html).count()
}
