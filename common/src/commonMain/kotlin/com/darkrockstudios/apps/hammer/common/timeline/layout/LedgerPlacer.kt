package com.darkrockstudios.apps.hammer.common.timeline.layout

/** A row the ledger has to place. [pinned] rows (milestones) sit on their rule. */
data class LedgerCandidate(
	val eventId: Int,
	val kind: LayoutKind,
	val anchorY: Float,
	val anchorY1: Float?,
	val title: String,
	val sub: String?,
	val style: LedgerStyle,
	val mark: LedgerMark,
	val laneIndex: Int?,
	val pinned: Boolean,
	val order: Int,
	/** Where a lane callout's leader starts, at its mark in the lane. */
	val source: PointDp? = null,
)

/** [textY] is the first baseline; the row occupies [top, top + height). */
data class PlacedLedgerRow(
	val candidate: LedgerCandidate,
	val lines: List<String>,
	val sub: String?,
	val textY: Float,
	val top: Float,
	val height: Float,
) {
	val bottom: Float get() = top + height
}

/**
 * One-dimensional collision avoidance for the ledger. Rows are taken in anchor order. Pinned rows
 * sit a few dp below their anchor (pushed down only by an earlier pinned row they would overlap)
 * and reserve that stretch. Every other row starts at its anchor or below the previous unpinned
 * row, whichever is lower, then drops below any reserved stretch it would overlap.
 */
object LedgerPlacer {
	private const val ANCHOR_OFFSET = 4f
	private const val ROW_PAD = 4f
	private const val MAX_TITLE_LINES = 2

	fun place(
		candidates: List<LedgerCandidate>,
		textWidth: Float,
		measurer: TimelineTextMeasurer,
		metrics: LayoutMetrics,
	): List<PlacedLedgerRow> {
		val lineHeight = metrics.ledgerLineHeight
		val baselineOffset = lineHeight + 1f
		val sorted = candidates.sortedWith(compareBy<LedgerCandidate>({ it.anchorY }, { it.order }, { it.eventId }))

		val lines = sorted.map { c ->
			val style = if (c.style == LedgerStyle.Milestone) LabelStyle.LedgerMilestone else LabelStyle.Ledger
			LabelFitter.wrapToWidth(c.title, textWidth, style, measurer, MAX_TITLE_LINES)
		}
		val subs = sorted.map { c -> c.sub?.let { LabelFitter.ellipsize(it, textWidth, LabelStyle.LedgerSub, measurer) } }
		val heights = FloatArray(sorted.size) { i ->
			lines[i].size * lineHeight + (if (subs[i] != null) lineHeight else 0f) + ROW_PAD
		}
		val tops = FloatArray(sorted.size)

		// Reserved [top, bottom) stretches of pinned rows, in placement order (so sorted by top).
		val reservedTop = ArrayList<Float>()
		val reservedBottom = ArrayList<Float>()
		for (i in sorted.indices) {
			if (!sorted[i].pinned) continue
			var top = sorted[i].anchorY + ANCHOR_OFFSET - baselineOffset
			if (reservedBottom.isNotEmpty() && top < reservedBottom.last()) top = reservedBottom.last()
			tops[i] = top
			reservedTop += top
			reservedBottom += top + heights[i]
		}

		var previousBottom = Float.NEGATIVE_INFINITY
		for (i in sorted.indices) {
			if (sorted[i].pinned) continue
			val h = heights[i]
			var top = maxOf(sorted[i].anchorY + ANCHOR_OFFSET - baselineOffset, previousBottom)
			var moved = true
			while (moved) {
				moved = false
				for (r in reservedTop.indices) {
					if (top < reservedBottom[r] && top + h > reservedTop[r]) {
						top = reservedBottom[r]
						moved = true
					}
				}
			}
			tops[i] = top
			previousBottom = top + h
		}

		return sorted.indices.map { i ->
			PlacedLedgerRow(sorted[i], lines[i], subs[i], tops[i] + baselineOffset, tops[i], heights[i])
		}
	}

	/** Desktop ledger width: the widest title or subtitle plus padding, clamped to the metric range. */
	fun ledgerWidth(candidates: List<LedgerCandidate>, measurer: TimelineTextMeasurer, metrics: LayoutMetrics): Float {
		var widest = 0f
		for (c in candidates) {
			widest = maxOf(widest, measurer.widthDp(c.title, LabelStyle.Ledger))
			c.sub?.let { widest = maxOf(widest, measurer.widthDp(it, LabelStyle.LedgerSub)) }
		}
		return (widest + metrics.ledgerPad).coerceIn(metrics.ledgerMin, metrics.ledgerMax)
	}
}
