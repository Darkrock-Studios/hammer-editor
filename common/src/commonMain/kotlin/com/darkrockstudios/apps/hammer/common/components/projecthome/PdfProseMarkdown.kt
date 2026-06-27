package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.conamobile.pdfkmp.dsl.ContainerScope
import com.conamobile.pdfkmp.dsl.TextScope
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.unit.Sp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/** Theme accents for prose rendering; null falls back to neutral defaults. */
internal data class ProseColors(
	val primary: PdfColor? = null,
	val secondary: PdfColor? = null,
) {
	val link: PdfColor get() = primary ?: PdfColor.Blue
}

/**
 * Renders markdown prose into a PDF container with book typography: every paragraph gets
 * a first-line indent, and consecutive paragraphs run without a blank line between them —
 * the indent is the separator.
 *
 * Parsing uses the same intellij-markdown engine as the EPUB export (GFM flavour, so
 * `~~strikethrough~~` and pipe tables work) instead of pdfkmp's markdown module, whose
 * layout offers no paragraph indent control.
 */
internal fun ContainerScope.proseMarkdown(markdown: String, colors: ProseColors = ProseColors()) {
	val blocks = parseProseMarkdown(markdown)
	if (blocks.isEmpty()) return
	val base = TextStyle(lineHeight = Sp(TextStyle().fontSize.value * BODY_LEADING))
	column {
		blocks.forEachIndexed { index, block ->
			if (index > 0) {
				val previous = blocks[index - 1]
				when {
					// Consecutive paragraphs carry their own separation via the indent.
					previous is ProseBlock.Paragraph && block is ProseBlock.Paragraph -> Unit
					previous is ProseBlock.Heading -> spacer(height = HEADING_SPACING)
					else -> spacer(height = BLOCK_SPACING)
				}
			}
			when (block) {
				is ProseBlock.Paragraph -> renderParagraph(block.spans, base, colors)
				is ProseBlock.Heading -> renderHeading(block, base, colors)
				is ProseBlock.Listing -> renderListing(block, base, colors)
				is ProseBlock.Quote -> renderQuote(block, base, colors)
				is ProseBlock.CodeBlock -> renderCode(block, base)
				ProseBlock.Rule -> divider()
				is ProseBlock.Table -> renderTable(block, base, colors)
			}
		}
	}
}

/** One contiguous styled run of paragraph text. */
internal data class ProseSpan(
	val text: String,
	val bold: Boolean = false,
	val italic: Boolean = false,
	val strikethrough: Boolean = false,
	val code: Boolean = false,
	val link: String? = null,
)

/**
 * One item of a [ProseBlock.Listing]. Nested lists are flattened into the parent's [items] in reading
 * order, but each item keeps its own [level] (0-based nesting depth) and [ordered] flag so consumers
 * can reconstruct indentation and markers.
 */
internal data class ProseListItem(
	val spans: List<ProseSpan>,
	val level: Int,
	val ordered: Boolean,
)

/** A block-level markdown element, reduced to what the prose layout renders. */
internal sealed interface ProseBlock {
	data class Paragraph(val spans: List<ProseSpan>) : ProseBlock
	data class Heading(val level: Int, val spans: List<ProseSpan>) : ProseBlock
	data class Listing(val ordered: Boolean, val items: List<ProseListItem>) : ProseBlock
	data class Quote(val paragraphs: List<List<ProseSpan>>) : ProseBlock
	data class CodeBlock(val code: String) : ProseBlock
	data object Rule : ProseBlock
	data class Table(val header: List<List<ProseSpan>>, val rows: List<List<List<ProseSpan>>>) : ProseBlock
}

/**
 * Parses [markdown] into renderable blocks using the GFM flavour, so `~~strike~~`, pipe tables, and
 * autolinks are recognized. Unknown syntax degrades to paragraph text.
 */
internal fun parseProseMarkdown(markdown: String): List<ProseBlock> {
	val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
	return ProseWalker(markdown).blocks(root)
}

// ---------------------------------------------------------------------------
// AST walking
// ---------------------------------------------------------------------------

private val HEADING_LEVELS: Map<IElementType, Int> = mapOf(
	MarkdownElementTypes.ATX_1 to 1,
	MarkdownElementTypes.ATX_2 to 2,
	MarkdownElementTypes.ATX_3 to 3,
	MarkdownElementTypes.ATX_4 to 4,
	MarkdownElementTypes.ATX_5 to 5,
	MarkdownElementTypes.ATX_6 to 6,
	MarkdownElementTypes.SETEXT_1 to 1,
	MarkdownElementTypes.SETEXT_2 to 2,
)

private class ProseWalker(private val source: String) {

	fun blocks(root: ASTNode): List<ProseBlock> = root.children.mapNotNull { block(it) }

	private fun block(node: ASTNode): ProseBlock? = when (node.type) {
		MarkdownElementTypes.PARAGRAPH ->
			inline(node).ifEmpty { null }?.let { ProseBlock.Paragraph(it) }

		in HEADING_LEVELS -> heading(node)

		MarkdownElementTypes.UNORDERED_LIST ->
			ProseBlock.Listing(ordered = false, items = listItems(node, level = 0, ordered = false))

		MarkdownElementTypes.ORDERED_LIST ->
			ProseBlock.Listing(ordered = true, items = listItems(node, level = 0, ordered = true))

		MarkdownElementTypes.BLOCK_QUOTE ->
			quoteParagraphs(node).ifEmpty { null }?.let { ProseBlock.Quote(it) }

		MarkdownElementTypes.CODE_FENCE -> ProseBlock.CodeBlock(fenceContent(node))
		MarkdownElementTypes.CODE_BLOCK -> ProseBlock.CodeBlock(indentedCode(node))

		MarkdownTokenTypes.HORIZONTAL_RULE -> ProseBlock.Rule

		GFMElementTypes.TABLE -> table(node)

		// Reference-link definitions and inter-block whitespace produce no output.
		MarkdownElementTypes.LINK_DEFINITION,
		MarkdownTokenTypes.EOL,
		MarkdownTokenTypes.WHITE_SPACE,
		-> null

		else -> inline(node).ifEmpty { null }?.let { ProseBlock.Paragraph(it) }
	}

	private fun heading(node: ASTNode): ProseBlock? {
		val content = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT)
			?: node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT)
			?: return null
		val spans = inline(content).ifEmpty { return null }
		return ProseBlock.Heading(level = HEADING_LEVELS.getValue(node.type), spans = spans)
	}

	private fun listItems(listNode: ASTNode, level: Int, ordered: Boolean): List<ProseListItem> {
		val items = mutableListOf<ProseListItem>()
		for (item in listNode.children) {
			if (item.type != MarkdownElementTypes.LIST_ITEM) continue
			val spans = mutableListOf<ProseSpan>()
			fun flush() {
				if (spans.isNotEmpty()) {
					items += ProseListItem(spans.toList(), level = level, ordered = ordered)
					spans.clear()
				}
			}
			for (child in item.children) {
				when (child.type) {
					MarkdownElementTypes.PARAGRAPH -> {
						if (spans.isNotEmpty()) spans += ProseSpan("\n")
						spans += inline(child)
					}
					// Nested lists keep reading order: emit this item's text, then the deeper items.
					MarkdownElementTypes.UNORDERED_LIST -> {
						flush()
						items += listItems(child, level = level + 1, ordered = false)
					}
					MarkdownElementTypes.ORDERED_LIST -> {
						flush()
						items += listItems(child, level = level + 1, ordered = true)
					}
					else -> Unit
				}
			}
			flush()
		}
		return items
	}

	private fun quoteParagraphs(node: ASTNode): List<List<ProseSpan>> {
		val paragraphs = mutableListOf<List<ProseSpan>>()
		for (child in node.children) {
			when (child.type) {
				MarkdownElementTypes.BLOCK_QUOTE -> paragraphs += quoteParagraphs(child)
				MarkdownTokenTypes.BLOCK_QUOTE,
				MarkdownTokenTypes.EOL,
				MarkdownTokenTypes.WHITE_SPACE,
				-> Unit

				else -> inline(child).ifEmpty { null }?.let { paragraphs += it }
			}
		}
		return paragraphs
	}

	private fun fenceContent(node: ASTNode): String {
		val lines = mutableListOf<String>()
		var current: String? = null
		var pastOpeningLine = false
		for (child in node.children) {
			when (child.type) {
				MarkdownTokenTypes.CODE_FENCE_END -> break
				MarkdownTokenTypes.EOL -> {
					if (pastOpeningLine) lines += current.orEmpty()
					pastOpeningLine = true
					current = null
				}

				MarkdownTokenTypes.CODE_FENCE_CONTENT ->
					current = current.orEmpty() + child.getTextInNode(source)

				else -> Unit
			}
		}
		current?.let { lines += it }
		return lines.joinToString("\n")
	}

	private fun indentedCode(node: ASTNode): String =
		node.children
			.filter { it.type == MarkdownTokenTypes.CODE_LINE }
			.joinToString("\n") {
				it.getTextInNode(source).toString().removePrefix("    ").removePrefix("\t")
			}

	private fun table(node: ASTNode): ProseBlock.Table? {
		val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
			?.let { cells(it) } ?: return null
		val rows = node.children
			.filter { it.type == GFMElementTypes.ROW }
			.map { row -> cells(row) }
		return ProseBlock.Table(header = header, rows = rows)
	}

	private fun cells(row: ASTNode): List<List<ProseSpan>> =
		row.children.filter { it.type == GFMTokenTypes.CELL }.map { inline(it) }

	// -- inline content ------------------------------------------------------

	private data class Flags(
		val bold: Boolean = false,
		val italic: Boolean = false,
		val strikethrough: Boolean = false,
		val link: String? = null,
	)

	private fun inline(holder: ASTNode): List<ProseSpan> {
		val raw = mutableListOf<ProseSpan>()
		if (holder.children.isEmpty()) {
			collect(holder, Flags(), raw)
		} else {
			holder.children.forEach { collect(it, Flags(), raw) }
		}
		return normalise(raw)
	}

	private fun collect(node: ASTNode, flags: Flags, out: MutableList<ProseSpan>) {
		when (node.type) {
			MarkdownElementTypes.EMPH ->
				node.children.drop(1).dropLast(1)
					.forEach { collect(it, flags.copy(italic = true), out) }

			MarkdownElementTypes.STRONG ->
				node.children.drop(2).dropLast(2)
					.forEach { collect(it, flags.copy(bold = true), out) }

			GFMElementTypes.STRIKETHROUGH ->
				trimEqualDelimiters(node.children, GFMTokenTypes.TILDE)
					.forEach { collect(it, flags.copy(strikethrough = true), out) }

			MarkdownElementTypes.CODE_SPAN -> {
				val text = node.children
					.drop(1).dropLast(1)
					.joinToString("") { it.getTextInNode(source) }
					.replace('\n', ' ')
					.trim()
				if (text.isNotEmpty()) out += span(text, flags).copy(code = true)
			}

			MarkdownElementTypes.INLINE_LINK -> {
				val url = linkDestination(node)
				linkText(node)?.let { collect(it, flags.copy(link = url ?: flags.link), out) }
			}

			// Reference links can't be resolved without the link map; keep their text.
			MarkdownElementTypes.FULL_REFERENCE_LINK,
			MarkdownElementTypes.SHORT_REFERENCE_LINK,
			-> linkText(node)?.let { collect(it, flags, out) }

			// Images degrade to their alt text.
			MarkdownElementTypes.IMAGE ->
				node.findChildOfType(MarkdownElementTypes.INLINE_LINK)
					?.let { linkText(it) }
					?.let { collect(it, flags, out) }

			MarkdownElementTypes.LINK_TEXT ->
				node.children.drop(1).dropLast(1).forEach { collect(it, flags, out) }

			MarkdownElementTypes.AUTOLINK -> {
				val url = node.getTextInNode(source).toString().removeSurrounding("<", ">")
				out += span(url, flags.copy(link = url))
			}

			GFMTokenTypes.GFM_AUTOLINK -> {
				val url = node.getTextInNode(source).toString()
				out += span(url, flags.copy(link = url))
			}

			MarkdownTokenTypes.HARD_LINE_BREAK -> out += span("\n", flags)

			MarkdownTokenTypes.EOL,
			MarkdownTokenTypes.WHITE_SPACE,
			-> out += span(" ", flags)

			else -> if (node.children.isEmpty()) {
				out += span(unescapeMarkdown(node.getTextInNode(source)), flags)
			} else {
				node.children.forEach { collect(it, flags, out) }
			}
		}
	}

	private fun span(text: String, flags: Flags): ProseSpan = ProseSpan(
		text = text,
		bold = flags.bold,
		italic = flags.italic,
		strikethrough = flags.strikethrough,
		link = flags.link,
	)

	private fun linkText(node: ASTNode): ASTNode? =
		node.findChildOfType(MarkdownElementTypes.LINK_TEXT)

	private fun linkDestination(node: ASTNode): String? =
		node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
			?.getTextInNode(source)?.toString()
			?.removeSurrounding("<", ">")
			?.let { unescapeMarkdown(it) }
			?.takeIf { it.isNotBlank() }

	private fun trimEqualDelimiters(children: List<ASTNode>, delimiter: IElementType): List<ASTNode> {
		var left = 0
		var right = children.lastIndex
		while (left < right && children[left].type == delimiter && children[right].type == delimiter) {
			left++
			right--
		}
		return children.subList(left, right + 1)
	}

	/** Merges adjacent same-style runs, collapses doubled spaces, trims the paragraph edges. */
	private fun normalise(spans: List<ProseSpan>): List<ProseSpan> {
		val merged = mutableListOf<ProseSpan>()
		for (s in spans) {
			val last = merged.lastOrNull()
			if (last != null && !last.code && !s.code && last.copy(text = "") == s.copy(text = "")) {
				merged[merged.lastIndex] = last.copy(text = last.text + s.text)
			} else {
				merged += s
			}
		}
		// A hard break lexes as the trailing spaces plus a separate EOL, so spaces can
		// straddle the newline we emit; strip them rather than render a stray indent.
		val collapsed = merged.map { s ->
			if (s.code) s
			else s.copy(text = s.text.replace(SPACE_AROUND_NEWLINE, "\n").replace(MULTI_SPACE, " "))
		}
		return collapsed
			.mapIndexed { index, s ->
				var text = s.text
				if (index == 0) text = text.trimStart()
				if (index == collapsed.lastIndex) text = text.trimEnd()
				s.copy(text = text)
			}
			.filter { it.text.isNotEmpty() }
	}
}

private val MULTI_SPACE = Regex(" {2,}")
private val SPACE_AROUND_NEWLINE = Regex(" *\n *")

// ---------------------------------------------------------------------------
// Rendering onto the PdfKmp DSL
// ---------------------------------------------------------------------------

/**
 * First-line indent rendered as a run of no-break spaces (~1.3em). The layout engine
 * only treats ' ', '\t', '\n' as strippable whitespace, so NBSPs survive as glyphs on
 * the paragraph's first line and vanish into normal wrapping. Swap for a real
 * firstLineIndent parameter if pdfkmp grows one.
 */
private const val FIRST_LINE_INDENT = "\u00A0\u00A0\u00A0\u00A0\u00A0"

private val HEADING_SCALES = listOf(2.0f, 1.6f, 1.3f, 1.15f, 1.05f, 1.0f)
private val BLOCK_SPACING = Dp(8f)
private val HEADING_SPACING = Dp(12f)
private val CODE_BACKGROUND = PdfColor(0.95f, 0.95f, 0.95f)

/** Baseline-to-baseline distance as a multiple of font size. */
private const val BODY_LEADING = 1.5f
private const val HEADING_LEADING = 1.25f

private fun ContainerScope.renderParagraph(spans: List<ProseSpan>, base: TextStyle, colors: ProseColors) {
	// A paragraph that is exactly one link renders through the clickable link DSL
	// (flush — it reads as a block element, not prose); links inside running text
	// are styled but not clickable (no per-span link areas).
	val onlyLink = spans.singleOrNull()?.takeIf { it.link != null }
	if (onlyLink != null) {
		link(onlyLink.link!!) {
			text(onlyLink.text) {
				color = colors.link
				underline = true
				bold = onlyLink.bold
				italic = onlyLink.italic
				strikethrough = onlyLink.strikethrough
			}
		}
		return
	}
	richText {
		defaultSpanStyle = base
		lineHeight = base.lineHeight
		span(FIRST_LINE_INDENT)
		for (s in spans) span(s.text) { applyFlags(s, colors) }
	}
}

private fun ContainerScope.renderHeading(block: ProseBlock.Heading, base: TextStyle, colors: ProseColors) {
	val scale = HEADING_SCALES.getOrElse(block.level - 1) { 1f }
	val size = base.fontSize.value * scale
	val accent = when (block.level) {
		1 -> colors.primary
		2 -> colors.secondary
		else -> null
	}
	richText {
		defaultSpanStyle = base.copy(
			fontSize = Sp(size),
			fontWeight = FontWeight.Bold,
			color = accent ?: base.color,
		)
		lineHeight = Sp(size * HEADING_LEADING)
		for (s in block.spans) span(s.text) { applyFlags(s, colors) }
	}
}

private fun ContainerScope.renderListing(block: ProseBlock.Listing, base: TextStyle, colors: ProseColors) {
	column(spacing = Dp(4f)) {
		block.items.forEachIndexed { index, item ->
			val marker = if (block.ordered) "${index + 1}." else "•"
			row(verticalAlignment = VerticalAlignment.Top) {
				box(width = if (block.ordered) Dp(20f) else Dp(16f)) {
					aligned(BoxAlignment.TopStart) {
						text(marker) { color = base.color }
					}
				}
				weighted(1f) {
					richText {
						defaultSpanStyle = base
						lineHeight = base.lineHeight
						for (s in item.spans) span(s.text) { applyFlags(s, colors) }
					}
				}
			}
		}
	}
}

private fun ContainerScope.renderQuote(block: ProseBlock.Quote, base: TextStyle, colors: ProseColors) {
	column(
		padding = Padding(left = Dp(12f), top = Dp(4f), right = Dp(4f), bottom = Dp(4f)),
		borderEach = BorderSides(
			left = BorderStroke(width = Dp(3f), color = PdfColor.LightGray),
		),
		spacing = Dp(4f),
	) {
		val quoteStyle = base.copy(color = PdfColor.Gray)
		for (paragraph in block.paragraphs) {
			richText {
				defaultSpanStyle = quoteStyle
				lineHeight = base.lineHeight
				for (s in paragraph) span(s.text) { applyFlags(s, colors) }
			}
		}
	}
}

private fun ContainerScope.renderCode(block: ProseBlock.CodeBlock, base: TextStyle) {
	// No bundled monospace face; approximate with a smaller size on a grey card.
	card(background = CODE_BACKGROUND, cornerRadius = Dp(4f)) {
		for (line in block.code.split("\n")) {
			text(line.ifEmpty { " " }) {
				fontSize = Sp(base.fontSize.value * 0.9f)
				color = base.color
			}
		}
	}
}

private fun ContainerScope.renderTable(block: ProseBlock.Table, base: TextStyle, colors: ProseColors) {
	val columnCount = block.header.size.coerceAtLeast(1)
	table(
		columns = List(columnCount) { TableColumn.Weight(1f) },
		border = TableBorder(),
	) {
		header {
			for (cellSpans in block.header) {
				cell {
					richText {
						defaultSpanStyle = base.copy(fontWeight = FontWeight.Bold)
						for (s in cellSpans) span(s.text) { applyFlags(s, colors) }
					}
				}
			}
		}
		for (bodyRow in block.rows) {
			row {
				for (cellSpans in bodyRow.take(columnCount) + List((columnCount - bodyRow.size).coerceAtLeast(0)) { emptyList() }) {
					cell {
						richText {
							defaultSpanStyle = base
							for (s in cellSpans) span(s.text) { applyFlags(s, colors) }
						}
					}
				}
			}
		}
	}
}

private fun TextScope.applyFlags(s: ProseSpan, colors: ProseColors) {
	if (s.bold) bold = true
	if (s.italic) italic = true
	if (s.strikethrough) strikethrough = true
	if (s.code) fontSize = Sp(fontSize.value * 0.9f)
	if (s.link != null) {
		color = colors.link
		underline = true
	}
}
