package com.darkrockstudios.apps.hammer.utilities

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.html.TrimmingInlineHolderProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

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
	 */
	private val sanitizer: PolicyFactory = HtmlPolicyBuilder()
		.allowElements(
			"p", "br", "strong", "em", "b", "i", "u", "del",
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
	 * @param preserveLineBreaks Lay the text out the way the author typed it: every newline starts a
	 * new line and every blank line is a blank line, which is what the editor shows them. Story prose
	 * opts in; anywhere the markdown is a short piece of writing rather than a story, such as a bio or
	 * a policy page, wants CommonMark's usual reflowing.
	 * @return Sanitized HTML string safe for rendering
	 */
	fun markdownToSafeHtml(markdown: String, preserveLineBreaks: Boolean = false): String {
		if (markdown.isBlank()) return ""

		val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
		val providers = markdownFlavour.createHtmlGeneratingProviders(
			LinkMap.buildLinkMap(parsedTree, markdown),
			null,
		) + if (preserveLineBreaks) PROSE_PROVIDERS else emptyMap()

		val unsafeHtml = HtmlGenerator(markdown, parsedTree, providers).generateHtml()
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
		/** Upper bound on the breaks one run of blank lines can produce. */
		const val MAX_CONSECUTIVE_BREAKS = 6

		private val PROSE_PROVIDERS: Map<IElementType, GeneratingProvider> = mapOf(
			MarkdownElementTypes.PARAGRAPH to ProseParagraphProvider,
			MarkdownElementTypes.MARKDOWN_FILE to ProseContainerProvider("body"),
			MarkdownElementTypes.BLOCK_QUOTE to ProseContainerProvider("blockquote"),
		)

		private val STRIKETHROUGH_SPAN =
			Regex("""<span class="user-del">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
	}
}

/**
 * A paragraph of prose keeps the lines the author typed. CommonMark reflows a run of single-newline
 * lines into one block, which turns a page of dialogue into a wall of text that looks nothing like
 * what the writer sees in the editor; each line becomes its own paragraph instead.
 *
 * Paragraph siblings carry no vertical margin in the story stylesheet, so consecutive lines sit
 * directly under one another and each picks up the first-line indent, exactly as the editor draws
 * them.
 */
private object ProseParagraphProvider : TrimmingInlineHolderProvider() {
	override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
		visitor.consumeTagOpen(node, "p")
	}

	override fun closeTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
		visitor.consumeTagClose("p")
	}

	override fun processNode(
		visitor: HtmlGenerator.HtmlGeneratingVisitor,
		text: String,
		node: ASTNode,
	) {
		val children = childrenToRender(node)
		openTag(visitor, text, node)

		children.forEachIndexed { index, child ->
			when {
				// An edge newline would only open or close an empty paragraph.
				child.type == MarkdownTokenTypes.EOL -> {
					if (index != 0 && index != children.lastIndex) {
						closeTag(visitor, text, node)
						openTag(visitor, text, node)
					}
				}

				// The trailing spaces of a hard break are redundant: the newline itself breaks here.
				child.type == MarkdownTokenTypes.HARD_LINE_BREAK -> Unit

				child is LeafASTNode -> visitor.visitLeaf(child)
				else -> child.accept(visitor)
			}
		}

		closeTag(visitor, text, node)
	}
}

/**
 * Blank lines an author left between passages are deliberate white space, but CommonMark treats any
 * run of them as a single paragraph break. Each one becomes a break element so the spacing survives.
 *
 * Only prose gets them. Headings, lists and rules carry their own margins, and stacking blank lines
 * on top of those margins opens a gap the author never asked for.
 */
private class ProseContainerProvider(tag: String) : SimpleTagProvider(tag) {
	override fun processNode(
		visitor: HtmlGenerator.HtmlGeneratingVisitor,
		text: String,
		node: ASTNode,
	) {
		openTag(visitor, text, node)

		var newlines = 0
		var afterParagraph = false
		for (child in node.children) {
			when (child.type) {
				MarkdownTokenTypes.EOL -> newlines++
				// A quote's own '>' markers sit between the lines they mark, and neither they nor
				// stray indentation say anything about how far apart two passages are.
				MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.BLOCK_QUOTE -> Unit
				else -> {
					val isParagraph = child.type == MarkdownElementTypes.PARAGRAPH
					if (afterParagraph && isParagraph) {
						val breaks = (newlines - 1).coerceIn(0, MarkdownService.MAX_CONSECUTIVE_BREAKS)
						repeat(breaks) { visitor.consumeHtml(BREAK) }
					}
					child.accept(visitor)
					afterParagraph = isParagraph
					newlines = 0
				}
			}
		}

		closeTag(visitor, text, node)
	}

	private companion object {
		const val BREAK = "<br />"
	}
}
