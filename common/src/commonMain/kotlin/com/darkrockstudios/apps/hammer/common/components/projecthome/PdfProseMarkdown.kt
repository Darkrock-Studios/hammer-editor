package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.conamobile.pdfkmp.dsl.ContainerScope
import com.darkrockstudios.apps.hammer.base.markdown.ProseHtml
import com.darkrockstudios.apps.hammer.common.data.search.unescapeMarkdown
import com.conamobile.pdfkmp.dsl.TextScope
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.RichLine
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.layout.layoutRichText
import com.conamobile.pdfkmp.node.Span
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.FontStyle
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
 * Parsed prose with its text already wrapped into lines. pdfkmp cannot split a rich-text
 * paragraph, a row or a decorated container across pages, so each wrapped line is laid out as
 * its own node and page breaks fall between lines.
 *
 * @property layouts parallel to [blocks]; null for a block that needs no measuring.
 */
internal class PreparedProse(
	val blocks: List<ProseBlock>,
	val layouts: List<ProseLayout?>,
)

/** The measured part of one [ProseBlock]. */
internal sealed interface ProseLayout {
	data class Paragraph(val lines: List<RichLine>) : ProseLayout

	/** One list of lines per list item. */
	data class Listing(val items: List<List<RichLine>>) : ProseLayout

	/** One list of lines per quoted paragraph. */
	data class Quote(val paragraphs: List<List<RichLine>>) : ProseLayout

	/** @property width the widest code line, so every line's background spans the same width. */
	data class Code(val width: Float) : ProseLayout

	/** Body rows of wrapped cells. A source row too tall for a page arrives as several rows. */
	data class Table(val rows: List<List<List<RichLine>>>) : ProseLayout
}

/**
 * Parses [markdown] and wraps its text to [width], the width of the container
 * [proseMarkdown] will render into.
 */
internal fun prepareProse(
	markdown: String,
	colors: ProseColors,
	metrics: FontMetrics,
	width: Float,
	keepBlankLines: Boolean = true,
): PreparedProse {
	fun wrap(
		spans: List<ProseSpan>,
		style: TextStyle,
		lineWidth: Float,
		lineHeight: Sp = style.lineHeight,
		indent: Boolean = false,
	): List<RichLine> {
		val styled = spans.map { Span(it.text, style.withFlags(it, colors)) }.toMutableList()
		if (indent) {
			// Each line is laid out again on its own, where an indent in the first word's style
			// is one token with that word; measure it as one here too.
			val first = styled.firstOrNull()
			if (first != null && first.style == style) {
				styled[0] = first.copy(text = FIRST_LINE_INDENT + first.text)
			} else {
				styled.add(0, Span(FIRST_LINE_INDENT, style))
			}
		}
		val measured = layoutRichText(styled, lineWidth, style.align, lineHeight, metrics)
		// A line laid out alone would resolve its direction from its own first strong character.
		return measured.lines.map { line ->
			line.copy(
				segments = line.segments.map {
					it.copy(style = it.style.copy(direction = measured.resolvedDirection))
				},
			)
		}
	}

	val blocks = parseProseMarkdown(markdown, keepBlankLines)
	val layouts = blocks.map { block ->
		when (block) {
			is ProseBlock.Paragraph ->
				if (block.isStandaloneLink) null
				else ProseLayout.Paragraph(wrap(block.spans, BODY_STYLE, width, indent = true))

			is ProseBlock.Listing -> {
				val textWidth = width - block.markerWidth.value
				ProseLayout.Listing(block.items.map { wrap(it.spans, BODY_STYLE, textWidth) })
			}

			is ProseBlock.Quote -> {
				val textWidth = width - QUOTE_INSET_LEFT.value - QUOTE_INSET_RIGHT.value - QUOTE_BAR_WIDTH.value
				ProseLayout.Quote(block.paragraphs.map { wrap(it, QUOTE_STYLE, textWidth) })
			}

			is ProseBlock.CodeBlock -> {
				val widest = block.lines.maxOf { metrics.measure(it, CODE_STYLE).width }
				ProseLayout.Code(widest.coerceAtMost(width - CODE_PADDING.value * 2))
			}

			is ProseBlock.Table -> {
				val cellWidth = width / block.columnCount - TABLE_CELL_PADDING.value * 2 - TABLE_CELL_SLACK
				ProseLayout.Table(
					block.rows.flatMap { row ->
						val cells = List(block.columnCount) { column ->
							wrap(row.getOrElse(column) { emptyList() }, BODY_STYLE, cellWidth, lineHeight = Sp.Zero)
						}
						val tallest = cells.maxOf { it.size }
						if (tallest <= MAX_TABLE_ROW_LINES) {
							listOf(cells)
						} else {
							List((tallest + SPLIT_TABLE_ROW_LINES - 1) / SPLIT_TABLE_ROW_LINES) { part ->
								cells.map { it.drop(part * SPLIT_TABLE_ROW_LINES).take(SPLIT_TABLE_ROW_LINES) }
							}
						}
					},
				)
			}

			else -> null
		}
	}
	return PreparedProse(blocks, layouts)
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
internal fun ContainerScope.proseMarkdown(prose: PreparedProse, colors: ProseColors = ProseColors()) {
	val blocks = prose.blocks
	if (blocks.isEmpty()) return
	val base = BODY_STYLE
	column {
		blocks.forEachIndexed { index, block ->
			if (index > 0) {
				val previous = blocks[index - 1]
				when {
					// Prose carries its own separation: the indent, and the author's blank lines.
					previous.isProseLine && block.isProseLine -> Unit
					previous is ProseBlock.Heading -> spacer(height = HEADING_SPACING)
					else -> spacer(height = BLOCK_SPACING)
				}
			}
			val layout = prose.layouts[index]
			when (block) {
				is ProseBlock.Paragraph -> renderParagraph(block, layout as ProseLayout.Paragraph?, colors)
				ProseBlock.Blank -> spacer(height = Dp(TextStyle().fontSize.value * BODY_LEADING))
				is ProseBlock.Heading -> renderHeading(block, base, colors)
				is ProseBlock.Listing -> renderListing(block, layout as ProseLayout.Listing)
				is ProseBlock.Quote -> renderQuote(layout as ProseLayout.Quote)
				is ProseBlock.CodeBlock -> renderCode(block, layout as ProseLayout.Code)
				ProseBlock.Rule -> divider()
				is ProseBlock.Table -> renderTable(block, layout as ProseLayout.Table, base, colors)
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
	/** One line of prose as the author typed it. */
	data class Paragraph(val spans: List<ProseSpan>) : ProseBlock

	/** A blank line the author left between two passages. */
	data object Blank : ProseBlock

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
 *
 * Prose keeps the shape the author gave it, which is what the editor shows them: every newline
 * starts a new [ProseBlock.Paragraph] and every blank line becomes a [ProseBlock.Blank]. CommonMark
 * would reflow both away, turning a page of dialogue into one packed block.
 *
 * With [keepBlankLines] off a lone blank line is dropped, leaving paragraphs to run together, and a
 * run of two or more is reduced to one: the break between passages or scenes.
 */
internal fun parseProseMarkdown(markdown: String, keepBlankLines: Boolean = true): List<ProseBlock> {
	val source = ProseHtml.normalizeLineEndings(markdown)
	val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
	val blocks = ProseWalker(source).blocks(root)
	return if (keepBlankLines) blocks else blocks.withoutParagraphGaps()
}

private fun List<ProseBlock>.withoutParagraphGaps(): List<ProseBlock> {
	val out = mutableListOf<ProseBlock>()
	var blanks = 0
	for (block in this) {
		if (block == ProseBlock.Blank) {
			blanks++
			continue
		}
		if (blanks >= 2) out += ProseBlock.Blank
		blanks = 0
		out += block
	}
	return out
}

/** True for the block kinds that make up running prose, as opposed to a structural block. */
internal val ProseBlock.isProseLine: Boolean
	get() = this is ProseBlock.Paragraph || this is ProseBlock.Blank

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

	/**
	 * Walks the top level, keeping the blank lines between prose passages. A run of newlines
	 * separating two paragraphs is one more than the blank lines it contains; only prose gets them,
	 * because a heading, list or rule carries its own spacing already.
	 */
	fun blocks(root: ASTNode): List<ProseBlock> {
		val out = mutableListOf<ProseBlock>()
		var newlines = 0
		var afterProse = false

		for (child in root.children) {
			if (child.type == MarkdownTokenTypes.EOL) {
				newlines++
				continue
			}
			if (child.type == MarkdownTokenTypes.WHITE_SPACE) continue

			val produced = block(child)
			if (produced.isEmpty()) continue

			if (afterProse && produced.first().isProseLine) {
				val blanks = (newlines - 1).coerceIn(0, ProseHtml.MAX_CONSECUTIVE_BREAKS)
				repeat(blanks) { out += ProseBlock.Blank }
			}
			out += produced
			afterProse = produced.last().isProseLine
			newlines = 0
		}

		return out
	}

	private fun block(node: ASTNode): List<ProseBlock> = when (node.type) {
		MarkdownElementTypes.PARAGRAPH -> paragraphs(node)

		in HEADING_LEVELS -> listOfNotNull(heading(node))

		MarkdownElementTypes.UNORDERED_LIST ->
			listOf(ProseBlock.Listing(ordered = false, items = listItems(node, level = 0, ordered = false)))

		MarkdownElementTypes.ORDERED_LIST ->
			listOf(ProseBlock.Listing(ordered = true, items = listItems(node, level = 0, ordered = true)))

		MarkdownElementTypes.BLOCK_QUOTE ->
			listOfNotNull(quoteParagraphs(node).ifEmpty { null }?.let { ProseBlock.Quote(it) })

		MarkdownElementTypes.CODE_FENCE -> listOf(ProseBlock.CodeBlock(fenceContent(node)))
		MarkdownElementTypes.CODE_BLOCK -> listOf(ProseBlock.CodeBlock(indentedCode(node)))

		MarkdownTokenTypes.HORIZONTAL_RULE -> listOf(ProseBlock.Rule)

		GFMElementTypes.TABLE -> listOfNotNull(table(node))

		// Reference-link definitions produce no output.
		MarkdownElementTypes.LINK_DEFINITION -> emptyList()

		else -> paragraphs(node)
	}

	/** A paragraph node, split into one block per line the author typed. */
	private fun paragraphs(node: ASTNode): List<ProseBlock.Paragraph> =
		if (node.children.isEmpty()) {
			listOfNotNull(inline(node).ifEmpty { null }?.let { ProseBlock.Paragraph(it) })
		} else {
			lines(node).map { ProseBlock.Paragraph(it) }
		}

	/** The inline content of [holder], cut at every newline the author typed. */
	private fun lines(holder: ASTNode): List<List<ProseSpan>> {
		val lines = mutableListOf<List<ProseSpan>>()
		var current = mutableListOf<ProseSpan>()

		fun endLine() {
			normalise(current).ifEmpty { null }?.let { lines += it }
			current = mutableListOf()
		}

		for (child in holder.children) {
			when (child.type) {
				MarkdownTokenTypes.EOL -> endLine()

				// The trailing spaces of a hard break are redundant: the newline itself breaks here.
				MarkdownTokenTypes.HARD_LINE_BREAK -> Unit

				// A quote's own '>' markers sit inside the paragraph, on every line but the first.
				MarkdownTokenTypes.BLOCK_QUOTE -> Unit

				else -> collect(child, Flags(), current)
			}
		}
		endLine()

		return lines
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

				// A quoted passage keeps its lines, the same as prose outside the quote.
				else -> paragraphs += lines(child)
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

private val BODY_STYLE = TextStyle(lineHeight = Sp(TextStyle().fontSize.value * BODY_LEADING))
private val QUOTE_STYLE = BODY_STYLE.copy(color = PdfColor.Gray)

/** No bundled monospace face; code is approximated with a smaller size on a grey card. */
private val CODE_STYLE = TextStyle(fontSize = Sp(TextStyle().fontSize.value * 0.9f))

/** Lines of a paragraph kept on one page at either side of a page break. */
private const val MIN_LINES_AT_BREAK = 2

private val ITEM_SPACING = Dp(4f)
private val QUOTE_INSET_LEFT = Dp(12f)
private val QUOTE_INSET_RIGHT = Dp(4f)
private val QUOTE_INSET_VERTICAL = Dp(4f)
private val QUOTE_BAR_WIDTH = Dp(3f)
private val CODE_PADDING = Dp(12f)
private val CODE_CORNER = Dp(4f)
private val TABLE_CELL_PADDING = Dp(8f)

/** Shaved off the wrap width of a cell so table rules never push a wrapped line onto two. */
private const val TABLE_CELL_SLACK = 2f

/** Tallest a table row may grow before it is split into rows of [SPLIT_TABLE_ROW_LINES]. */
private const val MAX_TABLE_ROW_LINES = 30

/** Short enough that the split rows pack a page without leaving much of it empty. */
private const val SPLIT_TABLE_ROW_LINES = 8

/**
 * A paragraph that is exactly one link renders through the clickable link DSL (flush: it reads
 * as a block element, not prose); links inside running text are styled but not clickable.
 */
private val ProseBlock.Paragraph.isStandaloneLink: Boolean
	get() = spans.singleOrNull()?.link != null

private val ProseBlock.Listing.markerWidth: Dp
	get() = if (ordered) Dp(20f) else Dp(16f)

private val ProseBlock.CodeBlock.lines: List<String>
	get() = code.split("\n").map { it.ifEmpty { " " } }

private val ProseBlock.Table.columnCount: Int
	get() = header.size.coerceAtLeast(1)

/**
 * Lays out [count] lines one node each, holding the first and last [MIN_LINES_AT_BREAK]
 * together so a page break strands neither.
 */
private fun ContainerScope.breakableLines(count: Int, line: ContainerScope.(Int) -> Unit) {
	if (count == 0) return
	if (count < MIN_LINES_AT_BREAK * 2) {
		keepTogether { repeat(count) { line(it) } }
		return
	}
	keepTogether { for (i in 0 until MIN_LINES_AT_BREAK) line(i) }
	for (i in MIN_LINES_AT_BREAK until count - MIN_LINES_AT_BREAK) line(i)
	keepTogether { for (i in count - MIN_LINES_AT_BREAK until count) line(i) }
}

/** One already-wrapped line. */
private fun ContainerScope.proseLine(line: RichLine, lineHeight: Sp = BODY_STYLE.lineHeight) {
	if (line.segments.isEmpty()) {
		spacer(height = Dp(line.height))
		return
	}
	richText {
		this.lineHeight = lineHeight
		for (segment in line.segments) {
			defaultSpanStyle = segment.style
			span(segment.text)
		}
	}
}

private fun ContainerScope.renderParagraph(
	block: ProseBlock.Paragraph,
	layout: ProseLayout.Paragraph?,
	colors: ProseColors,
) {
	if (layout == null) {
		val onlyLink = block.spans.single()
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
	breakableLines(layout.lines.size) { proseLine(layout.lines[it]) }
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

private fun ContainerScope.renderListing(block: ProseBlock.Listing, layout: ProseLayout.Listing) {
	layout.items.forEachIndexed { index, lines ->
		if (index > 0) spacer(height = ITEM_SPACING)
		val marker = if (block.ordered) "${index + 1}." else "•"
		breakableLines(lines.size) { lineIndex ->
			row(verticalAlignment = VerticalAlignment.Top) {
				box(width = block.markerWidth) {
					if (lineIndex == 0) {
						aligned(BoxAlignment.TopStart) {
							text(marker) { color = BODY_STYLE.color }
						}
					}
				}
				weighted(1f) { proseLine(lines[lineIndex]) }
			}
		}
	}
}

private fun ContainerScope.renderQuote(layout: ProseLayout.Quote) {
	val startsParagraph = layout.paragraphs.flatMap { lines -> lines.indices.map { it == 0 } }
	val lines = layout.paragraphs.flatten()
	breakableLines(lines.size) { index ->
		// The bar is drawn per line; the gaps between paragraphs are padding so it runs unbroken.
		column(
			padding = Padding(
				left = QUOTE_INSET_LEFT,
				top = if (startsParagraph[index]) QUOTE_INSET_VERTICAL else Dp.Zero,
				right = QUOTE_INSET_RIGHT,
				bottom = if (index == lines.lastIndex) QUOTE_INSET_VERTICAL else Dp.Zero,
			),
			borderEach = BorderSides(
				left = BorderStroke(width = QUOTE_BAR_WIDTH, color = PdfColor.LightGray),
			),
		) {
			proseLine(lines[index])
		}
	}
}

private fun ContainerScope.renderCode(block: ProseBlock.CodeBlock, layout: ProseLayout.Code) {
	val lines = block.lines
	lines.forEachIndexed { index, line ->
		val first = index == 0
		val last = index == lines.lastIndex
		column(
			background = CODE_BACKGROUND,
			cornerRadiusEach = CornerRadius(
				topLeft = if (first) CODE_CORNER else Dp.Zero,
				topRight = if (first) CODE_CORNER else Dp.Zero,
				bottomLeft = if (last) CODE_CORNER else Dp.Zero,
				bottomRight = if (last) CODE_CORNER else Dp.Zero,
			),
			padding = Padding(
				left = CODE_PADDING,
				top = if (first) CODE_PADDING else Dp.Zero,
				right = CODE_PADDING,
				bottom = if (last) CODE_PADDING else Dp.Zero,
			),
		) {
			spacer(width = Dp(layout.width))
			text(line) {
				fontSize = CODE_STYLE.fontSize
				color = CODE_STYLE.color
			}
		}
	}
}

private fun ContainerScope.renderTable(
	block: ProseBlock.Table,
	layout: ProseLayout.Table,
	base: TextStyle,
	colors: ProseColors,
) {
	table(
		columns = List(block.columnCount) { TableColumn.Weight(1f) },
		border = TableBorder(),
		cellPadding = Padding.all(TABLE_CELL_PADDING),
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
		for (bodyRow in layout.rows) {
			row {
				for (lines in bodyRow) {
					cell {
						lines.forEach { proseLine(it, lineHeight = Sp.Zero) }
					}
				}
			}
		}
	}
}

private fun TextStyle.withFlags(s: ProseSpan, colors: ProseColors): TextStyle = copy(
	fontWeight = if (s.bold) FontWeight.Bold else fontWeight,
	fontStyle = if (s.italic) FontStyle.Italic else fontStyle,
	strikethrough = strikethrough || s.strikethrough,
	fontSize = if (s.code) Sp(fontSize.value * 0.9f) else fontSize,
	color = if (s.link != null) colors.link else color,
	underline = underline || s.link != null,
)

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
