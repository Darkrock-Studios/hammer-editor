package com.darkrockstudios.apps.hammer.base.diff

import kotlin.math.roundToInt

/**
 * Translates a source offset on one side of a diff to the corresponding offset on the other,
 * using the [DiffResult.anchors] as known matched points.
 *
 * Between two adjacent anchors the mapping is linear: equal regions map 1:1 (the spans are
 * identical length on both sides), and inside an edit hunk the offset is interpolated
 * proportionally across the two differing lengths. This is enough to keep two scrolled panes
 * roughly aligned on matching prose.
 */
class OffsetMap(anchors: List<DiffAnchor>) {

	// Defensive copy, sorted on the left axis. Anchors are already monotonic on both axes by
	// construction, but sorting guards against malformed input.
	private val anchors: List<DiffAnchor> =
		anchors.ifEmpty { listOf(DiffAnchor(0, 0)) }.sortedBy { it.leftSource }

	fun leftToRight(leftOffset: Int): Int =
		interpolate(leftOffset, from = { it.leftSource }, to = { it.rightSource })

	fun rightToLeft(rightOffset: Int): Int =
		interpolate(rightOffset, from = { it.rightSource }, to = { it.leftSource })

	private inline fun interpolate(
		offset: Int,
		from: (DiffAnchor) -> Int,
		to: (DiffAnchor) -> Int,
	): Int {
		val first = anchors.first()
		val last = anchors.last()
		if (offset <= from(first)) return to(first)
		if (offset >= from(last)) return to(last)

		// Find the last anchor whose `from` value is <= offset.
		var lo = 0
		var hi = anchors.size - 1
		while (lo < hi) {
			val mid = (lo + hi + 1) ushr 1
			if (from(anchors[mid]) <= offset) lo = mid else hi = mid - 1
		}

		val low = anchors[lo]
		val high = anchors.getOrElse(lo + 1) { low }
		val fromSpan = from(high) - from(low)
		val toSpan = to(high) - to(low)
		if (fromSpan <= 0) return to(low)

		val frac = (offset - from(low)).toDouble() / fromSpan
		return to(low) + (frac * toSpan).roundToInt()
	}
}
