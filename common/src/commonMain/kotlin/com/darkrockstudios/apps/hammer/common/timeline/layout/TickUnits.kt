package com.darkrockstudios.apps.hammer.common.timeline.layout

/** Ticks every [step] of [unit]. */
data class TickStep(val unit: TickUnit, val step: Long) {
	val size: Long get() = unit.sizeInBase * step
}

/** The positions a [frame] shows, before any padding the scale adds in dp. */
fun resolveFrame(events: List<LayoutEvent>, frame: TimelineFrame): LongRange = when (frame) {
	TimelineFrame.Everything -> extent(events) ?: 0L..0L
	TimelineFrame.Story -> extent(events.filter { it.kind == LayoutKind.SCENE }) ?: resolveFrame(events, TimelineFrame.Everything)
	is TimelineFrame.Band -> {
		val event = events.firstOrNull { it.id == frame.eventId }
		if (event == null) {
			resolveFrame(events, TimelineFrame.Everything)
		} else {
			val length = event.extentEnd - event.start
			val pad = maxOf(1L, length / BAND_PAD_DIVISOR)
			(event.start - pad)..(event.extentEnd + pad)
		}
	}
}

private const val BAND_PAD_DIVISOR = 20L

private fun extent(events: List<LayoutEvent>): LongRange? {
	if (events.isEmpty()) return null
	var lo = Long.MAX_VALUE
	var hi = Long.MIN_VALUE
	for (e in events) {
		lo = minOf(lo, e.start)
		hi = maxOf(hi, e.extentEnd)
	}
	return lo..hi
}

fun deriveTickUnit(range: LongRange, units: List<TickUnit>, targetTicks: Int): TickStep? =
	deriveTickUnit(range.last - range.first, units, targetTicks)

/**
 * The coarsest unit giving at least half of [targetTicks] across [length], then the smallest step
 * from 1, 2, 5, 10, 25, 50, 100, 250, 500, ... that keeps the count, plus [extraTicks] placed
 * outside [length], at or under 1.5 times [targetTicks].
 */
fun deriveTickUnit(length: Long, units: List<TickUnit>, targetTicks: Int, extraTicks: Int = 0): TickStep? {
	if (units.isEmpty()) return null
	val minCount = targetTicks / 2.0
	val unit = units.firstOrNull { length.toDouble() / it.sizeInBase >= minCount } ?: units.last()
	val maxCount = targetTicks * 1.5
	for (step in tickSteps()) {
		if (length.toDouble() / (unit.sizeInBase.toDouble() * step) + extraTicks <= maxCount) return TickStep(unit, step)
	}
	return TickStep(unit, Long.MAX_VALUE / unit.sizeInBase)
}

private fun tickSteps(): Sequence<Long> = sequence {
	yield(1L)
	yield(2L)
	yield(5L)
	var decade = 10L
	while (decade <= Long.MAX_VALUE / 100) {
		yield(decade)
		yield(decade * 5 / 2)
		yield(decade * 5)
		decade *= 10
	}
}

/** Multiples of the step size inside [range], at most [limit] of them. */
fun tickPositions(range: LongRange, unit: TickUnit, step: Long, limit: Int = 1000): List<Long> {
	val size = unit.sizeInBase * step
	if (size <= 0) return emptyList()
	val first = -(-range.first).floorDiv(size) * size
	val out = ArrayList<Long>()
	var p = first
	while (p <= range.last && out.size < limit) {
		out += p
		p += size
	}
	return out
}
