package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfAlignment
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBlock
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBookmark
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBorder
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfColor
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfDocument
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfDocumentWriter
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfFont
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfFontFamily
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfHyperlink
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfHyperlinkKind
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfInfo
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfInline
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfLineBreak
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfPageBreak
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfParagraph
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfParagraphStyle
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfSpanStyle
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfTextRun
import okio.BufferedSink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private const val TOC_TITLE = "Contents"

private val BODY_FONT = RtfFont("Georgia", RtfFontFamily.Roman)
private val MONO_FONT = RtfFont("Consolas", RtfFontFamily.Modern)

// Font sizes in half-points; the body is 12pt to match the other exporters.
private const val BODY_HALF_POINTS = 24
private const val TITLE_HALF_POINTS = 72
private const val AUTHOR_HALF_POINTS = 32
private const val TOC_TITLE_HALF_POINTS = 48

/** Heading 1..6 sizes in half-points, mirroring the DOCX renderer's scale. */
private val HEADING_HALF_POINTS = listOf(48, 36, 32, 28, 26, 24)

// Paragraph spacing/indent in twips (twentieths of a point); 1440 twips = 1 inch.
private const val BODY_FIRST_LINE_INDENT = 360
private const val PARAGRAPH_SPACE_AFTER = 160
private const val HEADING_SPACE_BEFORE = 360
private const val TITLE_SPACE_BEFORE = 3600
private const val SECTION_SPACE = 240
private const val QUOTE_INDENT = 720
private const val LIST_INDENT_PER_LEVEL = 360

private fun chapterBookmark(index: Int): String = "chapter${index + 1}"

/**
 * Renders the story as a Rich Text Format (.rtf) document mirroring the EPUB/DOCX layout: a title
 * page, a Contents page of links to chapter bookmarks, then one chapter per top-level node starting
 * on a fresh page. Chapter bodies are rendered from markdown into formatted RTF runs. Heading and
 * link colors pick up the project theme's accent colors.
 *
 * The document is assembled as a strongly-typed [RtfDocument] and serialized by [RtfDocumentWriter],
 * which keeps the output 7-bit ASCII (non-ASCII as `\uN` escapes) and portable across readers.
 */
fun writeStoryAsRtf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
	val primary = themeColor(projectData.theme?.primary)
	val secondary = themeColor(projectData.theme?.secondary)

	val blocks = buildList {
		addAll(titlePage(projectName, authorName, primary))
		addAll(contentsPage(effective, primary))
		effective.forEachIndexed { index, chapter -> addAll(chapterBlocks(index, chapter, primary, secondary)) }
	}

	val document = RtfDocument(
		blocks = blocks,
		defaultFont = BODY_FONT,
		defaultFontSizeHalfPoints = BODY_HALF_POINTS,
		info = RtfInfo(title = projectName, author = authorName),
		generator = "Hammer ${BuildMetadata.APP_VERSION}",
	)

	sink.writeUtf8(RtfDocumentWriter().write(document))
}

private fun titlePage(projectName: String, authorName: String?, primary: RtfColor?): List<RtfBlock> = buildList {
	add(
		RtfParagraph(
			content = listOf(
				RtfTextRun(
					projectName,
					RtfSpanStyle(bold = true, fontSizeHalfPoints = TITLE_HALF_POINTS, color = primary),
				),
			),
			style = RtfParagraphStyle(
				alignment = RtfAlignment.Center,
				spaceBeforeTwips = TITLE_SPACE_BEFORE,
				spaceAfterTwips = SECTION_SPACE,
			),
		),
	)
	if (authorName != null) {
		add(
			RtfParagraph(
				content = listOf(
					RtfTextRun("by $authorName", RtfSpanStyle(italic = true, fontSizeHalfPoints = AUTHOR_HALF_POINTS)),
				),
				style = RtfParagraphStyle(alignment = RtfAlignment.Center, spaceAfterTwips = SECTION_SPACE),
			),
		)
	}
}

private fun contentsPage(chapters: List<StoryChapter>, primary: RtfColor?): List<RtfBlock> = buildList {
	add(RtfPageBreak)
	add(
		RtfParagraph(
			content = listOf(RtfTextRun(TOC_TITLE, RtfSpanStyle(bold = true, fontSizeHalfPoints = TOC_TITLE_HALF_POINTS))),
			style = RtfParagraphStyle(
				alignment = RtfAlignment.Center,
				spaceBeforeTwips = SECTION_SPACE,
				spaceAfterTwips = SECTION_SPACE,
			),
		),
	)
	chapters.forEachIndexed { index, chapter ->
		val label = "${index + 1}. ${chapter.name}"
		add(
			RtfParagraph(
				content = listOf(
					RtfHyperlink(
						target = chapterBookmark(index),
						kind = RtfHyperlinkKind.Bookmark,
						content = listOf(RtfTextRun(label, RtfSpanStyle(underline = true, color = primary))),
					),
				),
				style = RtfParagraphStyle(spaceAfterTwips = PARAGRAPH_SPACE_AFTER),
			),
		)
	}
}

private fun chapterBlocks(
	index: Int,
	chapter: StoryChapter,
	primary: RtfColor?,
	secondary: RtfColor?,
): List<RtfBlock> = buildList {
	add(RtfPageBreak)
	add(
		RtfParagraph(
			content = listOf(
				RtfBookmark(
					name = chapterBookmark(index),
					content = listOf(
						RtfTextRun(
							"${index + 1}. ${chapter.name}",
							RtfSpanStyle(bold = true, fontSizeHalfPoints = HEADING_HALF_POINTS[0], color = primary),
						),
					),
				),
			),
			style = RtfParagraphStyle(
				spaceBeforeTwips = HEADING_SPACE_BEFORE,
				spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
				keepWithNext = true,
			),
		),
	)
	if (chapter.markdown.isNotBlank()) {
		addAll(MarkdownRtfRenderer(chapter.markdown, primary, secondary).render())
	}
}

/** Project themes store colors as ARGB hex (`#FFRRGGBB`); convert to an [RtfColor]. */
private fun themeColor(argb: String?): RtfColor? {
	val css = argb?.let(::argbHexToCssHex)?.removePrefix("#") ?: return null
	val value = css.toIntOrNull(16) ?: return null
	return RtfColor.fromRgb(value)
}

/**
 * Walks the markdown AST and produces [RtfBlock]s. Inline formatting is flattened into [RtfTextRun]s
 * carrying a cumulative [RtfSpanStyle], so nested emphasis (`**bold _italic_**`) becomes runs with the
 * combined style. Block structure (headings, lists, quotes, code) maps onto paragraph properties.
 */
private class MarkdownRtfRenderer(
	private val source: String,
	private val primary: RtfColor?,
	private val secondary: RtfColor?,
) {
	private val blocks = mutableListOf<RtfBlock>()
	private var listDepth = -1

	private data class BlockContext(
		val quote: Boolean = false,
		val listMarker: String? = null,
		val indentLevel: Int = 0,
	)

	fun render(): List<RtfBlock> {
		val tree = MarkdownParser(CommonMarkFlavourDescriptor()).buildMarkdownTreeFromString(source)
		renderBlocks(tree.children, BlockContext())
		return blocks
	}

	private fun renderBlocks(nodes: List<ASTNode>, blockCtx: BlockContext) {
		// Only the first paragraph of a list item carries the bullet/number; trailing blocks flow under it.
		var pendingMarker = blockCtx.listMarker
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.PARAGRAPH -> {
					addParagraph(blockCtx, pendingMarker, inlineRuns(node.children, baseStyle(blockCtx)))
					pendingMarker = null
				}

				MarkdownElementTypes.ATX_1 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 1)
				MarkdownElementTypes.ATX_2 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 2)
				MarkdownElementTypes.ATX_3 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 3)
				MarkdownElementTypes.ATX_4 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 4)
				MarkdownElementTypes.ATX_5 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 5)
				MarkdownElementTypes.ATX_6 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 6)
				MarkdownElementTypes.SETEXT_1 -> heading(node, MarkdownTokenTypes.SETEXT_CONTENT, 1)
				MarkdownElementTypes.SETEXT_2 -> heading(node, MarkdownTokenTypes.SETEXT_CONTENT, 2)

				MarkdownElementTypes.BLOCK_QUOTE -> renderBlocks(
					node.children,
					blockCtx.copy(quote = true),
				)

				MarkdownElementTypes.UNORDERED_LIST -> renderList(node, ordered = false, blockCtx)
				MarkdownElementTypes.ORDERED_LIST -> renderList(node, ordered = true, blockCtx)

				MarkdownElementTypes.CODE_FENCE -> codeFence(node)
				MarkdownElementTypes.CODE_BLOCK -> codeBlock(node)
				MarkdownElementTypes.LINK_DEFINITION -> Unit

				MarkdownTokenTypes.HORIZONTAL_RULE -> horizontalRule()

				MarkdownTokenTypes.EOL,
				MarkdownTokenTypes.WHITE_SPACE,
				MarkdownTokenTypes.BLOCK_QUOTE,
				MarkdownTokenTypes.LIST_BULLET,
				MarkdownTokenTypes.LIST_NUMBER,
					-> Unit

				else -> {
					if (node.children.isNotEmpty()) {
						renderBlocks(node.children, blockCtx)
					} else {
						val literal = node.getTextInNode(source).toString()
						if (literal.isNotBlank()) {
							addParagraph(blockCtx, pendingMarker, listOf(RtfTextRun(unescape(literal), baseStyle(blockCtx))))
							pendingMarker = null
						}
					}
				}
			}
		}
	}

	private fun renderList(node: ASTNode, ordered: Boolean, blockCtx: BlockContext) {
		listDepth++
		var itemNumber = 1
		node.children
			.filter { it.type == MarkdownElementTypes.LIST_ITEM }
			.forEach { item ->
				val marker = if (ordered) "${itemNumber++}.\t" else "•\t"
				renderBlocks(
					item.children,
					blockCtx.copy(listMarker = marker, indentLevel = listDepth + 1),
				)
			}
		listDepth--
	}

	private fun heading(node: ASTNode, contentType: Any, level: Int) {
		val content = node.children.firstOrNull { it.type == contentType }
		val halfPoints = HEADING_HALF_POINTS[(level - 1).coerceIn(0, HEADING_HALF_POINTS.lastIndex)]
		val color = when (level) {
			1 -> primary
			2 -> secondary
			else -> null
		}
		val style = RtfSpanStyle(bold = true, fontSizeHalfPoints = halfPoints, color = color)
		val children = content?.children.orEmpty()
			.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
			.dropLastWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
		val runs = when {
			children.isNotEmpty() -> inlineRuns(children, style)
			content != null -> listOf(RtfTextRun(unescape(content.getTextInNode(source).toString()).trim(), style))
			else -> emptyList()
		}
		blocks.add(
			RtfParagraph(
				content = coalesce(runs),
				style = RtfParagraphStyle(
					spaceBeforeTwips = HEADING_SPACE_BEFORE,
					spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
					keepWithNext = true,
				),
			),
		)
	}

	private fun addParagraph(blockCtx: BlockContext, listMarker: String?, content: List<RtfInline>) {
		val style = when {
			listMarker != null -> RtfParagraphStyle(
				leftIndentTwips = LIST_INDENT_PER_LEVEL * blockCtx.indentLevel,
				firstLineIndentTwips = -LIST_INDENT_PER_LEVEL,
				spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
			)

			blockCtx.quote -> RtfParagraphStyle(
				leftIndentTwips = QUOTE_INDENT,
				spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
			)

			else -> RtfParagraphStyle(
				firstLineIndentTwips = BODY_FIRST_LINE_INDENT,
				spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
			)
		}
		val full = buildList {
			if (listMarker != null) add(RtfTextRun(listMarker, baseStyle(blockCtx)))
			addAll(content)
		}
		blocks.add(RtfParagraph(coalesce(full), style))
	}

	private fun baseStyle(blockCtx: BlockContext): RtfSpanStyle =
		if (blockCtx.quote) RtfSpanStyle(italic = true) else RtfSpanStyle.Default

	private fun inlineRuns(nodes: List<ASTNode>, style: RtfSpanStyle): List<RtfInline> {
		val out = mutableListOf<RtfInline>()
		appendInline(out, nodes, style)
		return out
	}

	private fun appendInline(out: MutableList<RtfInline>, nodes: List<ASTNode>, style: RtfSpanStyle) {
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.STRONG ->
					appendInline(out, node.children.stripDelimiters(MarkdownTokenTypes.EMPH), style.copy(bold = true))

				MarkdownElementTypes.EMPH ->
					appendInline(out, node.children.stripDelimiters(MarkdownTokenTypes.EMPH), style.copy(italic = true))

				MarkdownElementTypes.CODE_SPAN -> {
					val code = node.children
						.filter { it.type != MarkdownTokenTypes.BACKTICK }
						.joinToString("") { it.getTextInNode(source).toString() }
					out.add(RtfTextRun(code.removeSurrounding(" "), style.copy(font = MONO_FONT)))
				}

				MarkdownElementTypes.INLINE_LINK -> inlineLink(out, node, style)
				MarkdownElementTypes.AUTOLINK -> autoLink(out, node, style)

				MarkdownTokenTypes.HARD_LINE_BREAK -> out.add(RtfLineBreak)
				MarkdownTokenTypes.EOL -> out.add(RtfTextRun(" ", style))

				else -> {
					if (node.children.isEmpty()) {
						out.add(RtfTextRun(unescape(node.getTextInNode(source).toString()), style))
					} else {
						appendInline(out, node.children, style)
					}
				}
			}
		}
	}

	private fun inlineLink(out: MutableList<RtfInline>, node: ASTNode, style: RtfSpanStyle) {
		val destination =
			node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
				?.getTextInNode(source)?.toString()?.removeSurrounding("<", ">")
		val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
		if (destination == null || linkText == null) {
			out.add(RtfTextRun(unescape(node.getTextInNode(source).toString()), style))
			return
		}
		val content = inlineRuns(
			linkText.children.filter {
				it.type != MarkdownTokenTypes.LBRACKET && it.type != MarkdownTokenTypes.RBRACKET
			},
			style.copy(underline = true, color = primary),
		)
		out.add(RtfHyperlink(target = destination, content = content))
	}

	private fun autoLink(out: MutableList<RtfInline>, node: ASTNode, style: RtfSpanStyle) {
		val url = node.getTextInNode(source).toString().removeSurrounding("<", ">")
		out.add(
			RtfHyperlink(
				target = url,
				content = listOf(RtfTextRun(url, style.copy(underline = true, color = primary))),
			),
		)
	}

	private fun codeFence(node: ASTNode) {
		collectCodeLines(node, MarkdownTokenTypes.CODE_FENCE_CONTENT).forEach { codeParagraph(it) }
	}

	private fun codeBlock(node: ASTNode) {
		collectCodeLines(node, MarkdownTokenTypes.CODE_LINE)
			.map { it.removePrefix("    ").removePrefix("\t") }
			.forEach { codeParagraph(it) }
	}

	private fun collectCodeLines(node: ASTNode, contentType: Any): List<String> {
		val lines = mutableListOf<String>()
		val current = StringBuilder()
		for (child in node.children) {
			when (child.type) {
				contentType -> current.append(child.getTextInNode(source))
				MarkdownTokenTypes.EOL -> {
					lines += current.toString()
					current.clear()
				}
			}
		}
		if (current.isNotEmpty()) lines += current.toString()
		return lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
	}

	private fun codeParagraph(line: String) {
		blocks.add(
			RtfParagraph(
				content = listOf(RtfTextRun(line, RtfSpanStyle(font = MONO_FONT))),
				style = RtfParagraphStyle(spaceAfterTwips = 0),
			),
		)
	}

	private fun horizontalRule() {
		blocks.add(
			RtfParagraph(
				content = emptyList(),
				style = RtfParagraphStyle(spaceAfterTwips = PARAGRAPH_SPACE_AFTER, bottomBorder = RtfBorder()),
			),
		)
	}

	/** Merges consecutive [RtfTextRun]s that share a style so plain prose stays in a single run. */
	private fun coalesce(inlines: List<RtfInline>): List<RtfInline> {
		val result = mutableListOf<RtfInline>()
		for (inline in inlines) {
			val last = result.lastOrNull()
			if (inline is RtfTextRun && last is RtfTextRun && last.style == inline.style) {
				result[result.lastIndex] = last.copy(text = last.text + inline.text)
			} else {
				result.add(inline)
			}
		}
		return result
	}

	private fun List<ASTNode>.stripDelimiters(delimiterType: Any): List<ASTNode> =
		dropWhile { it.type == delimiterType }.dropLastWhile { it.type == delimiterType }

	/** Resolves CommonMark backslash escapes to their bare punctuation. */
	private fun unescape(text: String): String {
		if (!text.contains('\\')) return text
		val sb = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val c = text[i]
			val next = if (i + 1 < text.length) text[i + 1] else null
			if (c == '\\' && next != null && next in ASCII_PUNCTUATION) {
				sb.append(next)
				i += 2
			} else {
				sb.append(c)
				i++
			}
		}
		return sb.toString()
	}

	private companion object {
		const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
	}
}
