package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.markdown.ProseHtml
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import java.util.regex.Pattern

/**
 * Service for converting Markdown to sanitized HTML.
 *
 * This service uses the JetBrains Markdown library for parsing and
 * OWASP HTML Sanitizer to prevent XSS attacks by only allowing
 * safe HTML elements and attributes.
 *
 * The flavour is GitHub Flavored Markdown, matching the editor that writes the
 * content: CommonMark has no strikethrough, so `~~struck~~` reached the page as
 * literal tildes.
 */
class MarkdownService {
	private val markdownFlavour = GFMFlavourDescriptor()
	private val markdownParser = MarkdownParser(markdownFlavour)

	/**
	 * HTML sanitizer policy that allows only safe elements commonly used in markdown output.
	 * - Strips all script tags and event handlers
	 * - Only allows http/https URLs in links
	 * - Adds rel="nofollow" to all links for security
	 *
	 * An element the parser can produce but the policy drops does not vanish: its text survives
	 * unwrapped, so a stripped table reads as its cells run together. Everything GFM emits is
	 * either allowed here or, like an image or a task-list checkbox, deliberately left out.
	 */
	private val sanitizer: PolicyFactory = HtmlPolicyBuilder()
		.allowElements(
			"p", "br", "strong", "em", "b", "i", "u", "del",
			"ul", "ol", "li",
			"h1", "h2", "h3", "h4", "h5", "h6",
			"blockquote", "code", "pre",
			"table", "thead", "tbody", "tr", "th", "td",
			"a", "hr"
		)
		.allowUrlProtocols("http", "https")
		.allowAttributes("href").onElements("a")
		// A list that starts at 5 has to say so, or it renders as 1.
		.allowAttributes("start").matching(ORDERED_LIST_START).onElements("ol")
		.allowAttributes("align").matching(COLUMN_ALIGNMENT).onElements("th", "td")
		.requireRelNofollowOnLinks()
		.toFactory()

	/**
	 * Converts markdown text to sanitized HTML.
	 *
	 * The output HTML is safe to render in a browser without risk of XSS attacks.
	 * All script tags, event handlers, and javascript: URLs are stripped.
	 *
	 * @param markdown The markdown text to convert
	 * @param preserveLineBreaks Lay the text out the way the author typed it: every newline starts a
	 * new line and every blank line is a blank line, which is what the editor shows them. Story prose
	 * opts in; anywhere the markdown is a short piece of writing rather than a story, such as a bio or
	 * a policy page, wants CommonMark's usual reflowing.
	 * @return Sanitized HTML string safe for rendering
	 */
	fun markdownToSafeHtml(markdown: String, preserveLineBreaks: Boolean = false): String {
		if (markdown.isBlank()) return ""

		val source = ProseHtml.normalizeLineEndings(markdown)
		val parsedTree = markdownParser.buildMarkdownTreeFromString(source)
		val linkMap = LinkMap.buildLinkMap(parsedTree, source)
		val providers = if (preserveLineBreaks) {
			ProseHtml.providers(markdownFlavour, linkMap)
		} else {
			markdownFlavour.createHtmlGeneratingProviders(linkMap, null)
		}

		val unsafeHtml = HtmlGenerator(source, parsedTree, providers).generateHtml()
		return sanitizer.sanitize(unsafeHtml.strikethroughAsDel())
	}

	/**
	 * The GFM generator emits strikethrough as `<span class="user-del">`. Rewrite it
	 * to `<del>` so the sanitizer, which allows no attributes on a `span` and so
	 * would unwrap it, can keep the element.
	 */
	private fun String.strikethroughAsDel(): String =
		replace(STRIKETHROUGH_SPAN, "<del>$1</del>")

	companion object {
		private val STRIKETHROUGH_SPAN =
			Regex("""<span class="user-del">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

		private val ORDERED_LIST_START: Pattern = Pattern.compile("[0-9]{1,9}")
		private val COLUMN_ALIGNMENT: Pattern = Pattern.compile("left|center|right")
	}
}
