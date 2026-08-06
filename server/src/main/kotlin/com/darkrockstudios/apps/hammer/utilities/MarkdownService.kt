package com.darkrockstudios.apps.hammer.utilities

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

/**
 * Service for converting Markdown to sanitized HTML.
 *
 * This service uses the JetBrains Markdown library for parsing and
 * OWASP HTML Sanitizer to prevent XSS attacks by only allowing
 * safe HTML elements and attributes.
 */
class MarkdownService {
	private val markdownFlavour = CommonMarkFlavourDescriptor()
	private val markdownParser = MarkdownParser(markdownFlavour)

	/**
	 * HTML sanitizer policy that allows only safe elements commonly used in markdown output.
	 * - Strips all script tags and event handlers
	 * - Only allows http/https URLs in links
	 * - Adds rel="nofollow" to all links for security
	 */
	private val sanitizer: PolicyFactory = HtmlPolicyBuilder()
		.allowElements(
			"p", "br", "strong", "em", "b", "i", "u",
			"ul", "ol", "li",
			"h1", "h2", "h3", "h4", "h5", "h6",
			"blockquote", "code", "pre",
			"a", "hr"
		)
		.allowUrlProtocols("http", "https")
		.allowAttributes("href").onElements("a")
		.requireRelNofollowOnLinks()
		.toFactory()

	/**
	 * Converts markdown text to sanitized HTML.
	 *
	 * The output HTML is safe to render in a browser without risk of XSS attacks.
	 * All script tags, event handlers, and javascript: URLs are stripped.
	 *
	 * @param markdown The markdown text to convert
	 * @return Sanitized HTML string safe for rendering
	 */
	fun markdownToSafeHtml(markdown: String): String {
		if (markdown.isBlank()) return ""

		val source = preserveBlankLines(markdown)
		val parsedTree = markdownParser.buildMarkdownTreeFromString(source)
		val unsafeHtml = HtmlGenerator(source, parsedTree, markdownFlavour).generateHtml()
		return sanitizer.sanitize(unsafeHtml)
	}

	/**
	 * CommonMark collapses any run of blank lines into a single paragraph break, which loses the
	 * deliberate white space a writer put between passages. Each blank line past the first becomes
	 * a `<br />` block so the rendered story keeps the author's spacing.
	 */
	private fun preserveBlankLines(markdown: String): String {
		val out = StringBuilder(markdown.length)
		var inFence = false
		var blankRun = 0
		var seenContent = false

		for (line in markdown.lineSequence()) {
			if (inFence) {
				out.append(line).append('\n')
				if (isFenceDelimiter(line)) inFence = false
				continue
			}

			if (line.isBlank()) {
				blankRun++
				continue
			}

			if (blankRun > 0) {
				out.append('\n')
				if (seenContent) {
					repeat((blankRun - 1).coerceAtMost(MAX_CONSECUTIVE_BREAKS)) {
						out.append(BREAK_BLOCK).append("\n\n")
					}
				}
				blankRun = 0
			}

			out.append(line).append('\n')
			seenContent = true
			if (isFenceDelimiter(line)) inFence = true
		}

		return out.toString()
	}

	private fun isFenceDelimiter(line: String): Boolean {
		val trimmed = line.trimStart()
		if (line.length - trimmed.length > MAX_FENCE_INDENT) return false
		return trimmed.startsWith("```") || trimmed.startsWith("~~~")
	}

	companion object {
		/** Upper bound on the breaks one run of blank lines can produce. */
		const val MAX_CONSECUTIVE_BREAKS = 6

		/** A blank line preceding this makes CommonMark pass it through as a raw HTML block. */
		private const val BREAK_BLOCK = "<br />"

		private const val MAX_FENCE_INDENT = 3
	}
}
