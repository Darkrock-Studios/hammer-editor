package com.darkrockstudios.apps.hammer.common.data.export

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
					// Prose carries its own separation: the indent, and the author's blank lines.
					previous.isProseLine && block.isProseLine -> Unit
					previous is ProseBlock.Heading -> spacer(height = HEADING_SPACING)
					else -> spacer(height = BLOCK_SPACING)
				}
			}
			when (block) {
				is ProseBlock.Paragraph -> renderParagraph(block.spans, base, colors)
				ProseBlock.Blank -> spacer(height = Dp(TextStyle().fontSize.value * BODY_LEADING))
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
