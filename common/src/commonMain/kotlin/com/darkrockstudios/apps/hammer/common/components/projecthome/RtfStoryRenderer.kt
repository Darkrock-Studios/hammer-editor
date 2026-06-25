package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import okio.BufferedSink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private const val TOC_TITLE = "Contents"

private const val BODY_FONT = "Georgia"
private const val MONOSPACE_FONT = "Consolas"

// Font indices in the RTF font table.
private const val FONT_BODY = 0
private const val FONT_MONO = 1

// Color indices in the RTF color table; index 0 is the document default ("auto").
private const val COLOR_PRIMARY = 1
private const val COLOR_SECONDARY = 2

// RTF font sizes are expressed in half-points; the body is 12pt to match the other exporters.
private const val BODY_HALF_POINTS = 24
private const val TITLE_HALF_POINTS = 72
private const val TOC_TITLE_HALF_POINTS = 48

/** Heading 1..6 sizes in half-points, mirroring the DOCX renderer's scale. */
private val HEADING_HALF_POINTS = listOf(48, 36, 32, 28, 26, 24)

// Paragraph spacing/indent in twips (twentieths of a point); 1440 twips = 1 inch.
private const val BODY_FIRST_LINE_INDENT = 360
private const val PARAGRAPH_SPACE_AFTER = 160
private const val HEADING_SPACE_BEFORE = 360
private const val QUOTE_INDENT = 720
private const val LIST_INDENT_PER_LEVEL = 360

private fun chapterBookmark(index: Int): String = "chapter${index + 1}"

/**
 * Renders the story as a Rich Text Format (.rtf) document mirroring the EPUB/DOCX layout: a title
 * page, a Contents page of links to chapter bookmarks, then one chapter per top-level node starting
 * on a fresh page. Chapter bodies are rendered from markdown into formatted RTF runs. Heading and
 * link colors pick up the project theme's accent colors.
 *
 * RTF is plain ASCII text built from control words; non-ASCII characters are emitted as `\uN`
 * escapes so the output stays portable regardless of the reader's code page.
 */
fun writeStoryAsRtf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }

	val rtf = RtfBuilder(projectData.theme)
	rtf.writeHeader(projectName, authorName)
	rtf.writeTitlePage(projectName, authorName)
	rtf.writeContentsPage(effective)
	effective.forEachIndexed { index, chapter -> rtf.writeChapter(index, chapter) }
	rtf.writeFooter()

	sink.writeUtf8(rtf.toString())
}

private class RtfBuilder(theme: ProjectTheme?) {
	private val out = StringBuilder()

	private val primary = theme?.primary?.let(::argbHexToRtfColor)
	private val secondary = theme?.secondary?.let(::argbHexToRtfColor)

	override fun toString(): String = out.toString()

	fun writeHeader(projectName: String, authorName: String?) {
		out.append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1033")

		// Font table: \f0 is the prose font, \f1 the monospace font for code.
		out.append("{\\fonttbl")
		out.append("{\\f$FONT_BODY\\froman\\fcharset0 ").append(BODY_FONT).append(";}")
		out.append("{\\f$FONT_MONO\\fmodern\\fcharset0 ").append(MONOSPACE_FONT).append(";}")
		out.append("}")

		// Color table: index 0 is "auto"; accent colors keep stable indices even when absent
		// (a null accent falls back to black, which simply renders as the default text color).
		out.append("{\\colortbl;")
		out.append(primary ?: "\\red0\\green0\\blue0;")
		out.append(secondary ?: "\\red0\\green0\\blue0;")
		out.append("}")

		// Document metadata so the title/author surface in the reader's properties panel.
		out.append("{\\info{\\title ").append(escapeRtf(projectName)).append("}")
		if (authorName != null) out.append("{\\author ").append(escapeRtf(authorName)).append("}")
		out.append("}")

		out.append("\\fs$BODY_HALF_POINTS\n")
	}

	fun writeFooter() {
		out.append("}")
	}

	fun writeTitlePage(projectName: String, authorName: String?) {
		out.append("\\pard\\qc\\sb3600\\sa240")
		out.append("{\\b\\fs$TITLE_HALF_POINTS")
		if (primary != null) out.append("\\cf$COLOR_PRIMARY")
		out.append(' ').append(escapeRtf(projectName)).append("}\\par\n")

		if (authorName != null) {
			out.append("\\pard\\qc\\sa240")
			out.append("{\\i\\fs32 ").append(escapeRtf("by $authorName")).append("}\\par\n")
		}
	}

	fun writeContentsPage(chapters: List<StoryChapter>) {
		out.append("\\page\n")
		out.append("\\pard\\qc\\sb240\\sa240")
		out.append("{\\b\\fs$TOC_TITLE_HALF_POINTS ").append(escapeRtf(TOC_TITLE)).append("}\\par\n")

		chapters.forEachIndexed { index, chapter ->
			out.append("\\pard\\sa$PARAGRAPH_SPACE_AFTER ")
			val label = "${index + 1}. ${chapter.name}"
			// A HYPERLINK field jumping to the chapter's bookmark; the \fldrslt holds the visible text.
			out.append("{\\field{\\*\\fldinst HYPERLINK \\\\l ")
			out.append('"').append(escapeRtf(chapterBookmark(index))).append('"')
			out.append("}{\\fldrslt ")
			if (primary != null) out.append("{\\cf$COLOR_PRIMARY\\ul ").append(escapeRtf(label)).append("}")
			else out.append("{\\ul ").append(escapeRtf(label)).append("}")
			out.append("}}\\par\n")
		}
	}

	fun writeChapter(index: Int, chapter: StoryChapter) {
		out.append("\\page\n")
		out.append("\\pard\\sb$HEADING_SPACE_BEFORE\\sa$PARAGRAPH_SPACE_AFTER\\keepn")
		out.append("{\\b\\fs${HEADING_HALF_POINTS[0]}")
		if (primary != null) out.append("\\cf$COLOR_PRIMARY")
		out.append(' ')
		// Bookmark target for the contents links, wrapping the heading text.
		out.append("{\\*\\bkmkstart ").append(escapeRtf(chapterBookmark(index))).append("}")
		out.append(escapeRtf("${index + 1}. ${chapter.name}"))
		out.append("{\\*\\bkmkend ").append(escapeRtf(chapterBookmark(index))).append("}")
		out.append("}\\par\n")

		if (chapter.markdown.isNotBlank()) {
			MarkdownRtfWriter(out, chapter.markdown, primary != null, secondary != null).render()
		}
	}

	/** Project themes store colors as ARGB hex (`#FFRRGGBB`); RTF wants `\redN\greenN\blueN;`. */
	private fun argbHexToRtfColor(argb: String): String? {
		val css = argbHexToCssHex(argb)?.removePrefix("#") ?: return null
		val value = css.toIntOrNull(16) ?: return null
		val r = (value shr 16) and 0xFF
		val g = (value shr 8) and 0xFF
		val b = value and 0xFF
		return "\\red$r\\green$g\\blue$b;"
	}
}

/**
 * Walks the markdown AST and streams RTF paragraphs and runs. Inline formatting is emitted as
 * nested RTF groups (`{\b ...}`, `{\i ...}`) so character formatting is scoped and never leaks past
 * the run it applies to. Block structure (headings, lists, quotes, code) maps onto RTF paragraph
 * properties.
 */
private class MarkdownRtfWriter(
	private val out: StringBuilder,
	private val source: String,
	private val hasPrimary: Boolean,
	private val hasSecondary: Boolean,
) {
	private var listDepth = -1

	private data class BlockContext(
		val style: ParagraphStyle = ParagraphStyle.Body,
		val listMarker: String? = null,
		val indentLevel: Int = 0,
	)

	private enum class ParagraphStyle { Body, Quote }

	fun render() {
		val tree = MarkdownParser(CommonMarkFlavourDescriptor()).buildMarkdownTreeFromString(source)
		renderBlocks(tree.children, BlockContext())
	}

	private fun renderBlocks(nodes: List<ASTNode>, blockCtx: BlockContext) {
		// Only the first paragraph of a list item carries the bullet/number; trailing blocks flow under it.
		var pendingMarker = blockCtx.listMarker
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.PARAGRAPH -> {
					paragraph(blockCtx, pendingMarker) { renderInline(node.children) }
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
					blockCtx.copy(style = ParagraphStyle.Quote),
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
							paragraph(blockCtx, pendingMarker) { out.append(escapeRtf(literal)) }
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
		out.append("\\pard\\sb$HEADING_SPACE_BEFORE\\sa$PARAGRAPH_SPACE_AFTER\\keepn")
		out.append("{\\b\\fs$halfPoints")
		when {
			level == 1 && hasPrimary -> out.append("\\cf$COLOR_PRIMARY")
			level == 2 && hasSecondary -> out.append("\\cf$COLOR_SECONDARY")
		}
		out.append(' ')
		val children = content?.children.orEmpty()
			.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
			.dropLastWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
		if (children.isNotEmpty()) {
			renderInline(children)
		} else {
			content?.let { out.append(escapeRtf(unescape(it.getTextInNode(source).toString()).trim())) }
		}
		out.append("}\\par\n")
	}

	private fun paragraph(blockCtx: BlockContext, listMarker: String?, body: () -> Unit) {
		out.append("\\pard\\sa$PARAGRAPH_SPACE_AFTER")
		when {
			listMarker != null -> {
				val indent = LIST_INDENT_PER_LEVEL * blockCtx.indentLevel
				out.append("\\li$indent\\fi-$LIST_INDENT_PER_LEVEL")
			}

			blockCtx.style == ParagraphStyle.Quote -> out.append("\\li$QUOTE_INDENT")
			else -> out.append("\\fi$BODY_FIRST_LINE_INDENT")
		}
		out.append(' ')

		// Scope the quote italic in a group so the character formatting can't leak past this paragraph
		// (\pard resets paragraph properties but not run properties like \i).
		val italicQuote = blockCtx.style == ParagraphStyle.Quote
		if (italicQuote) out.append("{\\i ")
		if (listMarker != null) out.append(escapeRtf(listMarker))
		body()
		if (italicQuote) out.append("}")
		out.append("\\par\n")
	}

	private fun renderInline(nodes: List<ASTNode>) {
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.STRONG -> {
					out.append("{\\b ")
					renderInline(node.children.stripDelimiters(MarkdownTokenTypes.EMPH))
					out.append("}")
				}

				MarkdownElementTypes.EMPH -> {
					out.append("{\\i ")
					renderInline(node.children.stripDelimiters(MarkdownTokenTypes.EMPH))
					out.append("}")
				}

				MarkdownElementTypes.CODE_SPAN -> {
					val code = node.children
						.filter { it.type != MarkdownTokenTypes.BACKTICK }
						.joinToString("") { it.getTextInNode(source).toString() }
					out.append("{\\f$FONT_MONO ").append(escapeRtf(code.removeSurrounding(" "))).append("}")
				}

				MarkdownElementTypes.INLINE_LINK -> inlineLink(node)
				MarkdownElementTypes.AUTOLINK -> autoLink(node)

				MarkdownTokenTypes.HARD_LINE_BREAK -> out.append("\\line ")
				MarkdownTokenTypes.EOL -> out.append(' ')

				else -> {
					if (node.children.isEmpty()) {
						out.append(escapeRtf(unescape(node.getTextInNode(source).toString())))
					} else {
						renderInline(node.children)
					}
				}
			}
		}
	}

	private fun inlineLink(node: ASTNode) {
		val destination =
			node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
				?.getTextInNode(source)?.toString()?.removeSurrounding("<", ">")
		val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
		if (destination == null || linkText == null) {
			out.append(escapeRtf(unescape(node.getTextInNode(source).toString())))
			return
		}
		hyperlink(destination) {
			renderInline(
				linkText.children.filter {
					it.type != MarkdownTokenTypes.LBRACKET && it.type != MarkdownTokenTypes.RBRACKET
				}
			)
		}
	}

	private fun autoLink(node: ASTNode) {
		val url = node.getTextInNode(source).toString().removeSurrounding("<", ">")
		hyperlink(url) { out.append(escapeRtf(url)) }
	}

	private fun hyperlink(url: String, body: () -> Unit) {
		out.append("{\\field{\\*\\fldinst HYPERLINK ")
		out.append('"').append(escapeRtf(url)).append('"')
		out.append("}{\\fldrslt {")
		if (hasPrimary) out.append("\\cf$COLOR_PRIMARY")
		out.append("\\ul ")
		body()
		out.append("}}}")
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
		out.append("\\pard\\sa0 {\\f$FONT_MONO ").append(escapeRtf(line)).append("}\\par\n")
	}

	private fun horizontalRule() {
		// A bottom border on an empty paragraph draws a horizontal rule.
		out.append("\\pard\\brdrb\\brdrs\\brdrw10\\sa$PARAGRAPH_SPACE_AFTER\\par\n")
	}

	private fun List<ASTNode>.stripDelimiters(delimiterType: Any): List<ASTNode> =
		dropWhile { it.type == delimiterType }.dropLastWhile { it.type == delimiterType }

	/** Resolves CommonMark backslash escapes to their bare punctuation before RTF escaping. */
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

/**
 * Escapes text for an RTF body: the structural characters `\`, `{`, `}` are backslash-escaped,
 * tabs and newlines become control words, and any non-ASCII character is emitted as a `\uN`
 * escape with a `?` substitute so legacy readers still show something in its place.
 */
internal fun escapeRtf(text: String): String {
	val sb = StringBuilder(text.length)
	for (ch in text) {
		when {
			ch == '\\' || ch == '{' || ch == '}' -> sb.append('\\').append(ch)
			ch == '\t' -> sb.append("\\tab ")
			ch == '\n' -> sb.append("\\line ")
			ch == '\r' -> Unit
			ch.code in 0x20..0x7E -> sb.append(ch)
			else -> {
				// RTF \u takes a signed 16-bit value; chars above 0x7FFF must be expressed as negative.
				val code = if (ch.code > 0x7FFF) ch.code - 0x10000 else ch.code
				sb.append("\\u").append(code).append('?')
			}
		}
	}
	return sb.toString()
}
