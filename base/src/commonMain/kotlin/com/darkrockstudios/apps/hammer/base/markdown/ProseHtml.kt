package com.darkrockstudios.apps.hammer.base.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.html.TrimmingInlineHolderProvider
import org.intellij.markdown.parser.LinkMap

/**
 * How a story's markdown becomes HTML, shared by every surface that renders prose for a reader: the
 * web pages the server serves and the EPUB the client exports.
 *
 * CommonMark reflows prose. A run of single-newline lines collapses into one block and any run of
 * blank lines collapses to one paragraph break, so a page of dialogue arrives as a wall of text that
 * looks nothing like what the writer sees in the editor. These providers lay the text out as it was
 * typed instead: every newline starts a new line, and every blank line is a blank line.
 *
 * Only prose is touched. Lists, tables, code and headings keep markdown's own layout, and blank
 * lines next to them add nothing — those blocks carry their own spacing already.
 */
object ProseHtml {

	/** Upper bound on the breaks one run of blank lines can produce. */
	const val MAX_CONSECUTIVE_BREAKS = 6

	/**
	 * The generating providers [flavour] would use, with prose layout applied. Pass the result to
	 * [HtmlGenerator].
	 */
	fun providers(
		flavour: MarkdownFlavourDescriptor,
		linkMap: LinkMap,
	): Map<IElementType, GeneratingProvider> =
		flavour.createHtmlGeneratingProviders(linkMap, null) + mapOf(
			MarkdownElementTypes.PARAGRAPH to ProseParagraphProvider,
			MarkdownElementTypes.MARKDOWN_FILE to ProseContainerProvider("body"),
			MarkdownElementTypes.BLOCK_QUOTE to ProseContainerProvider("blockquote"),
		)

	/**
	 * The parser only ends a line on `\n`. Content written on Windows, or imported from a file that
	 * was, arrives with `\r\n` and would otherwise parse as one run-on paragraph.
	 */
	fun normalizeLineEndings(markdown: String): String = markdown.replace(CARRIAGE_RETURN, "\n")

	private val CARRIAGE_RETURN = Regex("\r\n?")
}

/**
 * A paragraph of prose keeps the lines the author typed: each becomes its own paragraph.
 *
 * This relies on the reader's stylesheet giving paragraph siblings no vertical margin, so
 * consecutive lines sit directly under one another and each picks up the first-line indent, exactly
 * as the editor draws them.
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

/** Emits a break for each blank line the author left between two paragraphs. */
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
						val breaks = (newlines - 1).coerceIn(0, ProseHtml.MAX_CONSECUTIVE_BREAKS)
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
