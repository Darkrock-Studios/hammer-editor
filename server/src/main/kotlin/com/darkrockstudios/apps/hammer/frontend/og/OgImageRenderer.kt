package com.darkrockstudios.apps.hammer.frontend.og

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Renders 1200x630 OpenGraph share cards with headless AWT. The Kingthings TTF and the Hammer
 * icon are loaded once; each render is stateless (a fresh image + graphics), so it's safe to
 * share across threads.
 *
 * The renderer draws whatever labels it's handed — all user-facing strings are localized upstream
 * and passed in, so nothing here is hard-coded English (the "Hammer" wordmark is a brand name).
 *
 * Text rendering needs native font libraries present (`fontconfig`/`libfreetype6`), which is why
 * the feature is gated behind [com.darkrockstudios.apps.hammer.ServerConfig.richLinkPreviews].
 */
class OgImageRenderer {
	private val brandFont: Font
	private val titleFont: Font
	private val subtitleFont: Font
	private val kickerFont: Font
	private val nameFont: Font
	private val attributionFont: Font
	private val icon: BufferedImage

	init {
		val base = (javaClass.getResourceAsStream(FONT_RESOURCE)
			?: error("Missing font resource $FONT_RESOURCE"))
			.use { Font.createFont(Font.TRUETYPE_FONT, it) }
		brandFont = base.deriveFont(Font.PLAIN, 56f)
		titleFont = base.deriveFont(Font.PLAIN, 76f)
		subtitleFont = base.deriveFont(Font.PLAIN, 44f)
		kickerFont = base.deriveFont(Font.PLAIN, 38f)
		nameFont = base.deriveFont(Font.PLAIN, 62f)
		attributionFont = base.deriveFont(Font.PLAIN, 36f)
		icon = (javaClass.getResourceAsStream(ICON_RESOURCE)
			?: error("Missing icon resource $ICON_RESOURCE"))
			.use { ImageIO.read(it) }
	}

	/** Author card: the Hammer wordmark up top, the pen name as the headline, a short subtitle. */
	fun render(title: String, subtitle: String?): ByteArray = canvas { g ->
		g.drawImage(icon, MARGIN, 62, 92, 92, null)
		g.color = INK
		g.font = brandFont
		g.drawString("Hammer", MARGIN + 92 + 22, 134)

		g.font = titleFont
		val metrics = g.fontMetrics
		var baseline = AUTHOR_TITLE_TOP
		for (line in wrapText(title, MAX_TEXT_WIDTH, AUTHOR_TITLE_LINES) { metrics.stringWidth(it) }) {
			g.drawString(line, MARGIN, baseline)
			baseline += AUTHOR_TITLE_STEP
		}

		if (!subtitle.isNullOrBlank()) {
			g.font = subtitleFont
			g.color = SECONDARY
			g.drawString(subtitle, MARGIN, baseline - AUTHOR_TITLE_STEP + AUTHOR_SUBTITLE_GAP)
		}
	}

	/**
	 * Story card: the story title is the hero (marked with a little open-book), the author flows
	 * below it, and the attribution ([attribution], e.g. "Written with Hammer") is pinned to the
	 * footer. [kicker] is the localized "A story by" lead-in above the author's name.
	 */
	fun renderStoryCard(
		title: String,
		author: String,
		kicker: String,
		attribution: String,
	): ByteArray = canvas { g ->
		g.font = titleFont
		g.color = INK
		val metrics = g.fontMetrics
		val lines = wrapTextIndented(
			title,
			firstLineWidth = MAX_TEXT_WIDTH - STORY_TITLE_INDENT,
			bodyWidth = MAX_TEXT_WIDTH,
			maxLines = STORY_TITLE_LINES,
		) { metrics.stringWidth(it) }
		var baseline = STORY_TITLE_TOP
		lines.forEachIndexed { index, line ->
			val x = if (index == 0) MARGIN + STORY_TITLE_INDENT else MARGIN
			g.drawString(line, x, baseline)
			baseline += STORY_TITLE_STEP
		}
		val lastTitleBaseline = baseline - STORY_TITLE_STEP
		g.drawBookMark(BOOK_X, STORY_BOOK_Y, BOOK_SIZE)

		g.font = kickerFont
		g.color = SECONDARY
		val kickerBaseline = lastTitleBaseline + STORY_KICKER_GAP
		g.drawString(kicker, MARGIN, kickerBaseline)
		g.font = nameFont
		g.color = INK
		g.drawString(author, MARGIN, kickerBaseline + STORY_NAME_GAP)

		g.drawAttribution(attribution)
	}

	private fun canvas(draw: (Graphics2D) -> Unit): ByteArray {
		val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
		val g = image.createGraphics()
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
			g.color = PAPER
			g.fillRect(0, 0, WIDTH, HEIGHT)
			g.color = ACCENT
			g.fillRect(0, HEIGHT - ACCENT_BAR, WIDTH, ACCENT_BAR)
			draw(g)
		} finally {
			g.dispose()
		}
		return ByteArrayOutputStream().use { out ->
			ImageIO.write(image, "png", out)
			out.toByteArray()
		}
	}

	private fun Graphics2D.drawAttribution(attribution: String) {
		val iconSize = 48
		drawImage(icon, MARGIN, ATTRIBUTION_BASELINE - 39, iconSize, iconSize, null)
		font = attributionFont
		color = SECONDARY
		drawString(attribution, MARGIN + iconSize + 18, ATTRIBUTION_BASELINE)
	}

	/** A small open-book glyph: two pages fanning up from a central spine. */
	private fun Graphics2D.drawBookMark(x: Double, y: Double, s: Double) {
		val cx = x + s / 2
		val topSpine = y + s * 0.30
		val topOuter = y + s * 0.14
		val botOuter = y + s * 0.72
		val botSpine = y + s * 0.86
		fun page(outerX: Double) = Path2D.Double().apply {
			moveTo(cx, topSpine)
			lineTo(outerX, topOuter)
			lineTo(outerX, botOuter)
			lineTo(cx, botSpine)
		}
		val previous = stroke
		stroke = BasicStroke((s * 0.07).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
		color = INK
		draw(page(x + s * 0.05))
		draw(page(x + s * 0.95))
		draw(Line2D.Double(cx, topSpine, cx, botSpine))
		stroke = previous
	}

	private companion object {
		const val WIDTH = 1200
		const val HEIGHT = 630
		const val MARGIN = 80
		const val ACCENT_BAR = 18
		const val MAX_TEXT_WIDTH = WIDTH - MARGIN * 2

		const val AUTHOR_TITLE_TOP = 250
		const val AUTHOR_TITLE_STEP = 86
		const val AUTHOR_TITLE_LINES = 3
		const val AUTHOR_SUBTITLE_GAP = 96

		const val BOOK_X = 80.0
		const val BOOK_SIZE = 72.0
		const val STORY_TITLE_INDENT = 108
		const val STORY_TITLE_TOP = 158
		const val STORY_BOOK_Y = 100.0
		const val STORY_TITLE_STEP = 86
		const val STORY_TITLE_LINES = 2
		const val STORY_KICKER_GAP = 112
		const val STORY_NAME_GAP = 70
		const val ATTRIBUTION_BASELINE = 586

		const val FONT_RESOURCE = "/assets/Kingthings_Trypewriter_2.ttf"
		const val ICON_RESOURCE = "/assets/images/hammer_icon.png"
		val PAPER = Color(255, 254, 249)
		val INK = Color(28, 25, 23)
		val SECONDARY = Color(87, 83, 78)
		val ACCENT = Color(217, 119, 6)
	}
}

/**
 * Greedy word-wrap of [text] into at most [maxLines] lines no wider than [maxWidth], measured by
 * [widthOf]. If the text doesn't fit, the last line is truncated with an ellipsis. A single word
 * wider than [maxWidth] gets its own (overflowing) line rather than being dropped.
 */
internal fun wrapText(
	text: String,
	maxWidth: Int,
	maxLines: Int,
	widthOf: (String) -> Int,
): List<String> = wrapTextIndented(text, firstLineWidth = maxWidth, bodyWidth = maxWidth, maxLines = maxLines, widthOf = widthOf)

/**
 * Greedy word-wrap where the first line is constrained to [firstLineWidth] (leaving room for an
 * inline icon) while every later line uses [bodyWidth]. The last line ellipsizes if the text runs
 * past [maxLines]; a single word wider than its line's width overflows onto its own line.
 */
internal fun wrapTextIndented(
	text: String,
	firstLineWidth: Int,
	bodyWidth: Int,
	maxLines: Int,
	widthOf: (String) -> Int,
): List<String> {
	val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
	if (words.isEmpty()) return emptyList()

	val lines = mutableListOf<String>()
	var line = ""
	var i = 0
	while (i < words.size) {
		val limit = if (lines.isEmpty()) firstLineWidth else bodyWidth
		val candidate = if (line.isEmpty()) words[i] else "$line ${words[i]}"
		if (line.isEmpty() || widthOf(candidate) <= limit) {
			line = candidate
			i++
		} else if (lines.size == maxLines - 1) {
			return lines + ellipsize(line, limit, widthOf)
		} else {
			lines += line
			line = ""
		}
	}
	if (line.isNotEmpty()) lines += line
	return lines
}

private fun ellipsize(line: String, maxWidth: Int, widthOf: (String) -> Int): String {
	var candidate = line
	while (candidate.isNotEmpty() && widthOf("$candidate…") > maxWidth) {
		candidate = candidate.dropLast(1).trimEnd()
	}
	return "$candidate…"
}
