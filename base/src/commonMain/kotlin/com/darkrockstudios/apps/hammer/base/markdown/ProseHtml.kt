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
import org.intellij.markdown.parser.MarkdownParser

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
 *
 * An aligned paragraph or heading (see [stripAlignmentWrappers]) carries its alignment as a class,
 * [ProseAlign.htmlClass], for the page's stylesheet to set `text-align` by.
 */
object ProseHtml {

	/** Upper bound on the breaks one run of blank lines can produce. */
	const val MAX_CONSECUTIVE_BREAKS = 6

	/**
	 * The generating providers [flavour] would use, with prose layout applied. Pass the result to
	 * [HtmlGenerator]. With [keepBlankLines] off, a lone blank line between paragraphs produces
	 * nothing and a run of two or more produces a single break.
	 */
	fun providers(
		flavour: MarkdownFlavourDescriptor,
		linkMap: LinkMap,
		keepBlankLines: Boolean = true,
		alignments: AlignedProse? = null,
	): Map<IElementType, GeneratingProvider> {
		val aligned = alignments?.takeIf { it.hasAlignments }
		return flavour.createHtmlGeneratingProviders(linkMap, null) + mapOf(
			MarkdownElementTypes.PARAGRAPH to ProseParagraphProvider(aligned),
			MarkdownElementTypes.MARKDOWN_FILE to ProseContainerProvider("body", keepBlankLines),
			MarkdownElementTypes.BLOCK_QUOTE to ProseContainerProvider("blockquote", keepBlankLines),
		) + alignedHeadingProviders(aligned)
	}

	/**
	 * [markdown] as HTML laid out by [providers], its alignment wrappers read into the classes of
	 * the paragraphs and headings they align. [extraProviders] adds to or overrides those providers.
	 */
	fun toHtml(
		markdown: String,
		flavour: MarkdownFlavourDescriptor,
		keepBlankLines: Boolean = true,
		extraProviders: (LinkMap) -> Map<IElementType, GeneratingProvider> = { emptyMap() },
	): String {
		val aligned = stripAlignmentWrappers(normalizeLineEndings(markdown))
		val source = aligned.markdown
		val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(source)
		val linkMap = LinkMap.buildLinkMap(tree, source)
		val providers = providers(flavour, linkMap, keepBlankLines, aligned) + extraProviders(linkMap)
		return HtmlGenerator(source, tree, providers).generateHtml()
	}

	private fun alignedHeadingProviders(alignments: AlignedProse?): Map<IElementType, GeneratingProvider> {
		if (alignments == null) return emptyMap()
		return HEADING_TAGS.mapValues { (_, tag) -> AlignedHeadingProvider(tag, alignments) }
	}

	private val HEADING_TAGS: Map<IElementType, String> = mapOf(
		MarkdownElementTypes.ATX_1 to "h1",
		MarkdownElementTypes.ATX_2 to "h2",
		MarkdownElementTypes.ATX_3 to "h3",
		MarkdownElementTypes.ATX_4 to "h4",
		MarkdownElementTypes.ATX_5 to "h5",
		MarkdownElementTypes.ATX_6 to "h6",
		MarkdownElementTypes.SETEXT_1 to "h1",
		MarkdownElementTypes.SETEXT_2 to "h2",
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
private class ProseParagraphProvider(private val alignments: AlignedProse?) : TrimmingInlineHolderProvider() {
	override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
		visitor.consumeTagOpen(node, "p")
	}

	override fun closeTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
		visitor.consumeTagClose("p")
	}

	/** Opens the paragraph of the line holding [offset], with its alignment's class when it has one. */
	private fun openLine(visitor: HtmlGenerator.HtmlGeneratingVisitor, node: ASTNode, offset: Int) {
		val align = alignments?.alignmentAt(offset)
		if (align == null) visitor.consumeTagOpen(node, "p") else visitor.consumeTagOpen(node, "p", align.classAttribute)
	}

	override fun processNode(
		visitor: HtmlGenerator.HtmlGeneratingVisitor,
		text: String,
		node: ASTNode,
	) {
		val children = childrenToRender(node)
		openLine(visitor, node, children.firstOrNull { it.type != MarkdownTokenTypes.EOL }?.startOffset ?: node.startOffset)

		children.forEachIndexed { index, child ->
			when {
				// An edge newline would only open or close an empty paragraph.
				child.type == MarkdownTokenTypes.EOL -> {
					if (index != 0 && index != children.lastIndex) {
						closeTag(visitor, text, node)
						openLine(visitor, node, children[index + 1].startOffset)
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

/** A heading, with its alignment's class when its line has one. */
private class AlignedHeadingProvider(tag: String, private val alignments: AlignedProse) : SimpleTagProvider(tag) {
	override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
		val align = alignments.alignmentAt(node.startOffset)
		if (align == null) super.openTag(visitor, text, node) else visitor.consumeTagOpen(node, tagName, align.classAttribute)
	}
}

private val ProseAlign.classAttribute: String get() = "class=\"$htmlClass\""

/** Emits a break for each blank line the author left between two paragraphs. */
private class ProseContainerProvider(
	tag: String,
	private val keepBlankLines: Boolean,
) : SimpleTagProvider(tag) {
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
						val blanks = (newlines - 1).coerceIn(0, ProseHtml.MAX_CONSECUTIVE_BREAKS)
						val breaks = when {
							keepBlankLines -> blanks
							blanks >= 2 -> 1
							else -> 0
						}
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
