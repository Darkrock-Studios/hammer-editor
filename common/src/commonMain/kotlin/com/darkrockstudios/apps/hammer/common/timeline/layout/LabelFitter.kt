package com.darkrockstudios.apps.hammer.common.timeline.layout

object LabelFitter {
	const val ELLIPSIS = "…"

	/** Inset of band text from the band's left edge. */
	private const val BAND_INSET = 6f
	private const val BAND_INSET_SMALL = 5f
	private const val BAND_PAD_X = 12f
	private const val BAND_PAD_X_SMALL = 10f
	private const val BAND_PAD_Y = 10f
	private const val SMALL_MIN_HEIGHT = 18f
	private const val ROTATE_MIN_WIDTH = 18f
	private const val ROTATE_PAD = 12f

	/** Greedy word wrap with no limit; a word wider than [maxWidth] stays whole on its own line. */
	fun wrapWords(text: String, maxWidth: Float, style: LabelStyle, measurer: TimelineTextMeasurer): List<String> {
		val lines = ArrayList<String>()
		var current = ""
		for (word in text.split(' ')) {
			if (word.isEmpty()) continue
			val candidate = if (current.isEmpty()) word else "$current $word"
			if (current.isEmpty() || measurer.widthDp(candidate, style) <= maxWidth) {
				current = candidate
			} else {
				lines += current
				current = word
			}
		}
		if (current.isNotEmpty() || lines.isEmpty()) lines += current
		return lines
	}

	/**
	 * [text] wrapped to at most [maxLines] lines no wider than [maxWidth]. A line cut short, by the
	 * line limit or by a word too long to fit, ends in an ellipsis.
	 */
	fun wrapToWidth(
		text: String,
		maxWidth: Float,
		style: LabelStyle,
		measurer: TimelineTextMeasurer,
		maxLines: Int,
	): List<String> {
		val all = wrapWords(text, maxWidth, style, measurer)
		val kept = if (all.size > maxLines) all.subList(0, maxLines) else all
		return kept.mapIndexed { i, line ->
			val cut = i == kept.lastIndex && all.size > kept.size
			if (cut) ellipsizeForced(line, maxWidth, style, measurer) else ellipsize(line, maxWidth, style, measurer)
		}
	}

	/** [text] if it fits, else its longest prefix that fits with an ellipsis; empty when not even that fits. */
	fun ellipsize(text: String, maxWidth: Float, style: LabelStyle, measurer: TimelineTextMeasurer): String {
		if (measurer.widthDp(text, style) <= maxWidth) return text
		return ellipsizeForced(text, maxWidth, style, measurer)
	}

	/** Like [ellipsize] but always marks the text as cut, for a line followed by text that was dropped. */
	private fun ellipsizeForced(text: String, maxWidth: Float, style: LabelStyle, measurer: TimelineTextMeasurer): String {
		if (measurer.widthDp(ELLIPSIS, style) > maxWidth) return ""
		var lo = 0
		var hi = text.length
		while (lo < hi) {
			val mid = (lo + hi + 1) ushr 1
			if (measurer.widthDp(text.substring(0, mid).trimEnd() + ELLIPSIS, style) <= maxWidth) lo = mid else hi = mid - 1
		}
		return text.substring(0, lo).trimEnd() + ELLIPSIS
	}

	/**
	 * Fits a band's title inside it, trying in turn: wrapped lines, one small line, one line
	 * rotated along the band, and finally [BandLabel.None] (the caller then places a leader label).
	 * Coordinates are relative to the band's top left corner.
	 */
	fun fitBandLabel(title: String, bandWidth: Float, bandHeight: Float, measurer: TimelineTextMeasurer): BandLabel {
		if (title.isBlank()) return BandLabel.None

		val lineHeight = measurer.lineHeightDp(LabelStyle.BandLabel)
		val wrapWidth = bandWidth - BAND_PAD_X
		val lines = wrapWords(title, wrapWidth, LabelStyle.BandLabel, measurer)
		val everyLineFits = lines.all { measurer.widthDp(it, LabelStyle.BandLabel) <= wrapWidth }
		if (everyLineFits && lines.size * lineHeight + BAND_PAD_Y <= bandHeight) {
			return BandLabel.Lines(lines, BAND_INSET, lineHeight + 1f, small = false)
		}

		val smallLineHeight = measurer.lineHeightDp(LabelStyle.BandLabelSmall)
		if (bandHeight >= SMALL_MIN_HEIGHT &&
			measurer.widthDp(title, LabelStyle.BandLabelSmall) <= bandWidth - BAND_PAD_X_SMALL
		) {
			return BandLabel.Lines(listOf(title), BAND_INSET_SMALL, smallLineHeight + 1f, small = true)
		}

		if (bandWidth >= ROTATE_MIN_WIDTH && measurer.widthDp(title, LabelStyle.BandLabel) <= bandHeight - ROTATE_PAD) {
			return BandLabel.Rotated(title, bandWidth / 2f + 4f, bandHeight / 2f)
		}

		return BandLabel.None
	}

	internal fun BandLabel.offset(dx: Float, dy: Float): BandLabel = when (this) {
		is BandLabel.Lines -> copy(xDp = xDp + dx, firstBaselineDp = firstBaselineDp + dy)
		is BandLabel.Rotated -> copy(cx = cx + dx, cy = cy + dy)
		is BandLabel.Leader, BandLabel.None -> this
	}
}
