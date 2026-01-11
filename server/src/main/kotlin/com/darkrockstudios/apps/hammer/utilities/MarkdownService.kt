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

		val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
		val unsafeHtml = HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()
		return sanitizer.sanitize(unsafeHtml)
	}
}
