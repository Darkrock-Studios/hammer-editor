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
	 * @param preserveBlankLines Keep runs of blank lines as visible space rather than letting
	 * CommonMark collapse them. Prose opts in; anywhere the markdown is a short piece of writing
	 * rather than a story, such as a bio or a policy page, wants the default collapsing.
	 * @return Sanitized HTML string safe for rendering
	 */
	fun markdownToSafeHtml(markdown: String, preserveBlankLines: Boolean = false): String {
		if (markdown.isBlank()) return ""

		val source = if (preserveBlankLines) expandBlankLines(markdown) else markdown
		val parsedTree = markdownParser.buildMarkdownTreeFromString(source)
		val unsafeHtml = HtmlGenerator(source, parsedTree, markdownFlavour).generateHtml()
		return sanitizer.sanitize(unsafeHtml)
	}

	/**
	 * CommonMark collapses any run of blank lines into a single paragraph break, which loses the
	 * deliberate white space a writer put between passages. Each blank line past the first becomes
	 * a `<br />` block so the rendered story keeps the author's spacing.
	 *
	 * Code is left exactly as written: a `<br />` landing inside a code block would both split the
	 * block and show up as literal text. Fenced blocks are tracked by their delimiter, and a blank
	 * run between two indented lines is assumed to sit inside an indented block.
	 */
	private fun expandBlankLines(markdown: String): String {
		val out = StringBuilder(markdown.length)
		var fence: Fence? = null
		var blankRun = 0
		var seenContent = false
		var lastIndent = 0

		for (line in markdown.lineSequence()) {
			val openFence = fence
			if (openFence != null) {
				out.append(line).append('\n')
				if (closesFence(line, openFence)) fence = null
				continue
			}

			if (line.isBlank()) {
				blankRun++
				continue
			}

			val indent = indentWidth(line)
			if (blankRun > 0) {
				val insideIndentedBlock = lastIndent > MAX_FENCE_INDENT && indent > MAX_FENCE_INDENT
				if (seenContent && !insideIndentedBlock) {
					out.append('\n')
					repeat((blankRun - 1).coerceAtMost(MAX_CONSECUTIVE_BREAKS)) {
						out.append(BREAK_BLOCK).append("\n\n")
					}
				} else {
					repeat(blankRun) { out.append('\n') }
				}
				blankRun = 0
			}

			out.append(line).append('\n')
			seenContent = true
			lastIndent = indent
			fence = openingFence(line)
		}

		return out.toString()
	}

	private fun openingFence(line: String): Fence? {
		if (indentWidth(line) > MAX_FENCE_INDENT) return null

		val trimmed = line.trimStart()
		val delimiter = trimmed.firstOrNull() ?: return null
		if (delimiter != '`' && delimiter != '~') return null

		val length = trimmed.takeWhile { it == delimiter }.length
		if (length < MIN_FENCE_LENGTH) return null
		// A backtick fence's info string may not itself contain a backtick.
		if (delimiter == '`' && trimmed.drop(length).contains('`')) return null

		return Fence(delimiter, length)
	}

	private fun closesFence(line: String, fence: Fence): Boolean {
		if (indentWidth(line) > MAX_FENCE_INDENT) return false

		val trimmed = line.trimStart()
		val length = trimmed.takeWhile { it == fence.delimiter }.length
		return length >= fence.length && trimmed.drop(length).isBlank()
	}

	private fun indentWidth(line: String): Int {
		var width = 0
		for (character in line) {
			when (character) {
				' ' -> width++
				'\t' -> width += TAB_WIDTH
				else -> return width
			}
		}
		return width
	}

	/** The delimiter that opened a code fence; only the same character, as long or longer, ends it. */
	private data class Fence(val delimiter: Char, val length: Int)

	companion object {
		/** Upper bound on the breaks one run of blank lines can produce. */
		const val MAX_CONSECUTIVE_BREAKS = 6

		/** A blank line preceding this makes CommonMark pass it through as a raw HTML block. */
		private const val BREAK_BLOCK = "<br />"

		private const val MAX_FENCE_INDENT = 3
		private const val MIN_FENCE_LENGTH = 3
		private const val TAB_WIDTH = 4
	}
}
