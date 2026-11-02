package com.darkrockstudios.apps.hammer.common.timeline.layout

/** The stretch [a, b] of positions drawn from [y0] to [y1]; [fold] marks a stretch drawn shorter than it is. */
data class ScaleSegment(val a: Long, val b: Long, val y0: Float, val y1: Float, val fold: Boolean)

/** A position-to-y mapping that is linear within each segment and monotonic overall. */
class PiecewiseScale internal constructor(
	val segments: List<ScaleSegment>,
	val top: Float,
	/** y of the last position. */
	val contentBottom: Float,
	padBottom: Float,
) {
	val height: Float = contentBottom + padBottom

	fun y(position: Long): Float {
		if (segments.isEmpty()) return top
		if (position <= segments.first().a) return top
		if (position >= segments.last().b) return contentBottom
		var lo = 0
		var hi = segments.size - 1
		while (lo < hi) {
			val mid = (lo + hi) ushr 1
			if (segments[mid].b < position) lo = mid + 1 else hi = mid
		}
		val s = segments[lo]
		val t = (position - s.a).toDouble() / (s.b - s.a).toDouble()
		return s.y0 + ((s.y1 - s.y0) * t).toFloat()
	}

	/** Total position length of the stretches drawn to scale. */
	val unfoldedLength: Long = segments.sumOf { if (it.fold) 0L else it.b - it.a }
}

/** First index of [sorted] holding a value at least [value]. */
internal fun lowerBound(sorted: LongArray, value: Long): Int {
	var lo = 0
	var hi = sorted.size
	while (lo < hi) {
		val mid = (lo + hi) ushr 1
		if (sorted[mid] < value) lo = mid + 1 else hi = mid
	}
	return lo
}

/** Distinct sorted breakpoints from [positions] plus [lo] and [hi]. */
internal fun breakpoints(positions: LongArray, lo: Long, hi: Long): LongArray {
	val all = LongArray(positions.size + 2)
	positions.copyInto(all)
	all[positions.size] = lo
	all[positions.size + 1] = hi
	all.sort()
	var n = 0
	for (i in all.indices) {
		if (n == 0 || all[i] != all[n - 1]) all[n++] = all[i]
	}
	return all.copyOf(n)
}

/**
 * Units per dp chosen so the median gap between consecutive breakpoints is drawn at [maxGap]: half
 * the gaps are then drawn to scale and the rest are capped, which keeps dense stretches readable
 * without a hand-tuned zoom.
 */
fun medianGapPxPerUnit(breakpoints: LongArray, maxGap: Float): Double {
	if (breakpoints.size < 2) return 1.0
	val gaps = LongArray(breakpoints.size - 1) { breakpoints[it + 1] - breakpoints[it] }
	gaps.sort()
	val n = gaps.size
	val median = if (n % 2 == 1) gaps[n / 2].toDouble() else (gaps[n / 2 - 1] + gaps[n / 2]) / 2.0
	return if (median > 0.0) maxGap / median else 1.0
}

/**
 * A to-scale axis with gap folding. A stretch between breakpoints is drawn at [pxPerUnit] but never
 * taller than `maxGap`, or `maxGap * coveredGapFactor` when one of [spans] covers it, and is marked
 * as a fold when its natural height exceeds its cap by `foldThreshold`.
 *
 * [breakpoints] must be sorted and distinct; [spans] are `[start, end]` pairs.
 */
fun buildScale(
	breakpoints: LongArray,
	spans: List<LongRange>,
	lo: Long,
	hi: Long,
	pxPerUnit: Double,
	metrics: LayoutMetrics,
): PiecewiseScale {
	val points = breakpoints.copyOfRange(lowerBound(breakpoints, lo), lowerBound(breakpoints, hi + 1))
	val segCount = (points.size - 1).coerceAtLeast(0)
	// Difference array over segment indices: how many spans cover each segment.
	val cover = IntArray(segCount + 1)
	for (span in spans) {
		val s = maxOf(span.first, lo)
		val e = minOf(span.last, hi)
		if (e <= s) continue
		val from = lowerBound(points, s)
		val to = lowerBound(points, e + 1) - 1
		if (to > from) {
			cover[from]++
			cover[to]--
		}
	}
	val segments = ArrayList<ScaleSegment>(segCount)
	var y = metrics.padTop
	var covered = 0
	for (i in 0 until segCount) {
		covered += cover[i]
		val a = points[i]
		val b = points[i + 1]
		val cap = if (covered > 0) metrics.maxGap * metrics.coveredGapFactor else metrics.maxGap
		val natural = ((b - a) * pxPerUnit).toFloat()
		val px = minOf(natural, cap)
		segments += ScaleSegment(a, b, y, y + px, natural > cap * metrics.foldThreshold)
		y += px
	}
	return PiecewiseScale(segments, metrics.padTop, y, metrics.padBottom)
}

/** Sequence view: every breakpoint gets its own row, `sequenceRowStep` apart, without folds. */
fun buildSequenceScale(breakpoints: LongArray, metrics: LayoutMetrics): PiecewiseScale {
	val segments = ArrayList<ScaleSegment>((breakpoints.size - 1).coerceAtLeast(0))
	var y = metrics.padTop
	for (i in 0 until breakpoints.size - 1) {
		segments += ScaleSegment(breakpoints[i], breakpoints[i + 1], y, y + metrics.sequenceRowStep, false)
		y += metrics.sequenceRowStep
	}
	return PiecewiseScale(segments, metrics.padTop, y, metrics.padBottom)
}
