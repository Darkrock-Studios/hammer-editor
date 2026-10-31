package com.darkrockstudios.apps.hammer.common.data.export

import com.conamobile.pdfkmp.dsl.ContainerScope
import com.conamobile.pdfkmp.dsl.TextScope
import com.conamobile.pdfkmp.geometry.ContentScale
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
import com.conamobile.pdfkmp.vector.VectorImage
import com.darkrockstudios.apps.hammer.base.image.ImageFormat

/** Theme accents for prose rendering; null falls back to neutral defaults. */
internal data class ProseColors(
	val primary: PdfColor? = null,
	val secondary: PdfColor? = null,
) {
	val link: PdfColor get() = primary ?: PdfColor.Blue
}

/** A project image in a form pdfkmp can draw. */
internal sealed interface ProsePicture {
	/** Height over width. */
	val aspectRatio: Float

	/** PNG or JPEG bytes. */
	class Raster(val bytes: ByteArray, override val aspectRatio: Float) : ProsePicture

	class Vector(val image: VectorImage, override val aspectRatio: Float) : ProsePicture
}

/**
 * [image] as pdfkmp can draw it: a raster as [exportRaster] gives it, SVG as a vector. Null, so the
 * image is written as its name, when [exportRaster] has none or an SVG has nothing to draw.
 */
internal fun prosePicture(image: ExportImage): ProsePicture? =
	if (ImageFormat.mimeType(image.fileExtension) == "image/svg+xml") {
		vectorPicture(image.bytes)
	} else {
		exportRaster(image)?.let { ProsePicture.Raster(it.bytes, it.aspectRatio) }
	}

private fun vectorPicture(bytes: ByteArray): ProsePicture? {
	val image = runCatching { VectorImage.parse(bytes.decodeToString()) }.getOrNull() ?: return null
	// pdfkmp sizes a vector the same way: its intrinsic size, else its viewport.
	val width = image.intrinsicWidth.takeIf { it > 0f } ?: image.viewportWidth
	val height = image.intrinsicHeight.takeIf { it > 0f } ?: image.viewportHeight
	// Text, embedded bitmaps and the like are not drawn, so such an SVG would come out blank.
	if (image.paths.isEmpty() || width <= 0f || height <= 0f) return null
	return ProsePicture.Vector(image, height / width)
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

	/** @property inset the left offset that centres the image in the column. */
	class Image(val picture: ProsePicture, val width: Float, val height: Float, val inset: Float) : ProseLayout
}

/**
 * Parses [markdown] and wraps its text to [width], the width of the container
 * [proseMarkdown] will render into.
 *
 * A project image [picture] has no drawing for is written as its name, as a line of prose. One it
 * has is drawn at [IMAGE_WIDTH_FRACTION] of [width], the share of the text width the editor gives
 * it, but no taller than [maxImageHeight].
 */
internal fun prepareProse(
	markdown: String,
	colors: ProseColors,
	metrics: FontMetrics,
	width: Float,
	keepBlankLines: Boolean = true,
	imageName: (imageId: Int) -> String? = { null },
	picture: (imageId: Int) -> ProsePicture? = { null },
	maxImageHeight: Float = Float.POSITIVE_INFINITY,
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

	val blocks = parseProseMarkdown(markdown, keepBlankLines, imageName).map { block ->
		if (block is ProseBlock.Image && picture(block.imageId) == null) ProseBlock.Paragraph(block.spans) else block
	}
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

			is ProseBlock.Image -> picture(block.imageId)?.let { imageLayout(it, width, maxImageHeight) }

			else -> null
		}
	}
	return PreparedProse(blocks, layouts)
}

private fun imageLayout(picture: ProsePicture, width: Float, maxHeight: Float): ProseLayout.Image {
	var imageWidth = width * IMAGE_WIDTH_FRACTION
	var imageHeight = imageWidth * picture.aspectRatio
	if (imageHeight > maxHeight) {
		imageHeight = maxHeight
		imageWidth = maxHeight / picture.aspectRatio
	}
	return ProseLayout.Image(picture, imageWidth, imageHeight, inset = (width - imageWidth) / 2)
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
					// An image sits a blank line from its neighbours, whether or not the author typed one.
					previous is ProseBlock.Image || block is ProseBlock.Image -> {
						if (previous != ProseBlock.Blank && block != ProseBlock.Blank) spacer(height = BLANK_LINE_HEIGHT)
					}

					// Prose carries its own separation: the indent, and the author's blank lines.
					previous.isProseLine && block.isProseLine -> Unit
					previous is ProseBlock.Heading -> spacer(height = HEADING_SPACING)
					else -> spacer(height = BLOCK_SPACING)
				}
			}
			val layout = prose.layouts[index]
			when (block) {
				is ProseBlock.Paragraph -> renderParagraph(block, layout as ProseLayout.Paragraph?, colors)
				ProseBlock.Blank -> spacer(height = BLANK_LINE_HEIGHT)
				is ProseBlock.Heading -> renderHeading(block, base, colors)
				is ProseBlock.Listing -> renderListing(block, layout as ProseLayout.Listing)
				is ProseBlock.Quote -> renderQuote(layout as ProseLayout.Quote)
				is ProseBlock.CodeBlock -> renderCode(block, layout as ProseLayout.Code)
				ProseBlock.Rule -> divider()
				is ProseBlock.Table -> renderTable(block, layout as ProseLayout.Table, base, colors)
				is ProseBlock.Image -> renderImage(block, layout as ProseLayout.Image)
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

/** One blank line of body text. */
private val BLANK_LINE_HEIGHT = Dp(TextStyle().fontSize.value * BODY_LEADING)

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

/** Kept whole, so an image that does not fit the rest of a page moves to the next. */
private fun ContainerScope.renderImage(block: ProseBlock.Image, layout: ProseLayout.Image) {
	keepTogether {
		column(padding = Padding(left = Dp(layout.inset))) {
			picture(layout.picture, layout.width, layout.height, altText = block.name)
		}
	}
}

/** Draws [picture] at [width] by [height] points. */
internal fun ContainerScope.picture(picture: ProsePicture, width: Float, height: Float, altText: String?) {
	when (picture) {
		is ProsePicture.Raster -> image(
			bytes = picture.bytes,
			width = Dp(width),
			height = Dp(height),
			contentScale = ContentScale.Fit,
			altText = altText,
		)

		is ProsePicture.Vector -> vector(picture.image, width = Dp(width), height = Dp(height))
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
