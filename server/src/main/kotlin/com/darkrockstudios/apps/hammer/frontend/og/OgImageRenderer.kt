package com.darkrockstudios.apps.hammer.frontend.og

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Renders 1200x630 OpenGraph share cards with headless AWT — a paper background, the Hammer
 * icon + wordmark, a wrapped/truncated title, and a subtitle. The Kingthings TTF and the icon
 * are loaded once; [render] is stateless per call (a fresh image + graphics), so it's safe to
 * share across threads.
 *
 * Text rendering needs native font libraries present (`fontconfig`/`libfreetype6`), which is why
 * the feature is gated behind [com.darkrockstudios.apps.hammer.ServerConfig.richLinkPreviews].
 */
class OgImageRenderer {
	private val brandFont: Font
	private val titleFont: Font
	private val subtitleFont: Font
	private val icon: BufferedImage

	init {
		val base = (javaClass.getResourceAsStream(FONT_RESOURCE)
			?: error("Missing font resource $FONT_RESOURCE"))
			.use { Font.createFont(Font.TRUETYPE_FONT, it) }
		brandFont = base.deriveFont(Font.PLAIN, 54f)
		titleFont = base.deriveFont(Font.PLAIN, 88f)
		subtitleFont = base.deriveFont(Font.PLAIN, 40f)
		icon = (javaClass.getResourceAsStream(ICON_RESOURCE)
			?: error("Missing icon resource $ICON_RESOURCE"))
			.use { ImageIO.read(it) }
	}

	fun render(title: String, subtitle: String?): ByteArray {
		val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
		val g = image.createGraphics()
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

			g.color = PAPER
			g.fillRect(0, 0, WIDTH, HEIGHT)
			g.color = ACCENT
			g.fillRect(0, HEIGHT - ACCENT_BAR, WIDTH, ACCENT_BAR)

			g.drawImage(icon, MARGIN, 64, 92, 92, null)
			g.color = INK
			g.font = brandFont
			g.drawString("Hammer", MARGIN + 92 + 22, 132)

			g.font = titleFont
			val titleMetrics = g.fontMetrics
			val lines = wrapText(title, MAX_TEXT_WIDTH, MAX_TITLE_LINES) { titleMetrics.stringWidth(it) }
			var y = 300
			for (line in lines) {
				g.drawString(line, MARGIN, y)
				y += titleMetrics.height + 6
			}

			if (!subtitle.isNullOrBlank()) {
				g.font = subtitleFont
				g.color = MUTE
				g.drawString(subtitle, MARGIN, y + 20)
			}
		} finally {
			g.dispose()
		}
		return ByteArrayOutputStream().use { out ->
			ImageIO.write(image, "png", out)
			out.toByteArray()
		}
	}

	private companion object {
		const val WIDTH = 1200
		const val HEIGHT = 630
		const val MARGIN = 80
		const val ACCENT_BAR = 18
		const val MAX_TEXT_WIDTH = WIDTH - MARGIN * 2
		const val MAX_TITLE_LINES = 3
		const val FONT_RESOURCE = "/assets/Kingthings_Trypewriter_2.ttf"
		const val ICON_RESOURCE = "/assets/images/hammer_icon.png"
		val PAPER = Color(255, 254, 249)
		val INK = Color(28, 25, 23)
		val MUTE = Color(120, 113, 108)
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
): List<String> {
	val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
	if (words.isEmpty()) return emptyList()

	val lines = mutableListOf<String>()
	var line = ""
	var i = 0
	while (i < words.size) {
		val candidate = if (line.isEmpty()) words[i] else "$line ${words[i]}"
		if (line.isEmpty() || widthOf(candidate) <= maxWidth) {
			line = candidate
			i++
		} else if (lines.size == maxLines - 1) {
			return lines + ellipsize(line, maxWidth, widthOf)
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
