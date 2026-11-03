package com.darkrockstudios.apps.hammer.common.timeline.layout

enum class LayoutKind { EVENT, ENTRY, SCENE }

enum class TimelineView { SEQUENCE, TO_SCALE }

sealed class TimelineFrame {
	data object Everything : TimelineFrame()

	/**
	 * The extent of the placed scenes, widened to take in milestones lying within a tenth of its
	 * length past either end; falls back to [Everything] when no scene is placed.
	 */
	data object Story : TimelineFrame()

	/** One event's span, padded so its edges are visible. */
	data class Band(val eventId: Int) : TimelineFrame()
}

/**
 * One timeline entry, already resolved to positions in the calendar's base unit.
 *
 * [precisionExtent] is how many base units the start date covers (a "year only" date covers a
 * year). A point with an extent above 1 lays out as a circa span over that extent.
 */
data class LayoutEvent(
	val id: Int,
	val kind: LayoutKind,
	val title: String,
	val subtitle: String?,
	val lane: String?,
	val milestone: Boolean,
	val order: Int,
	val start: Long,
	val end: Long?,
	val precisionExtent: Long = 1,
) {
	internal val isCirca: Boolean get() = end == null && precisionExtent > 1

	/** End of the drawn span, or null for a point. */
	internal val spanEnd: Long?
		get() = when {
			end != null -> maxOf(end, start)
			precisionExtent > 1 -> start + precisionExtent
			else -> null
		}

	internal val extentEnd: Long get() = spanEnd ?: start

	internal val laneName: String? get() = lane?.takeIf { it.isNotBlank() }
}

/**
 * A unit an axis may tick in. [steps] are the counts of it a tick may step by, finest first, where
 * the unit has natural ones (a quarter hour); past the last, or without any, steps run 1, 2, 5,
 * 10, 25, 50, ...
 */
data class TickUnit(val name: String, val sizeInBase: Long, val steps: List<Long> = emptyList())

/**
 * The units an axis may tick in, coarsest first, and how to label a tick. [format] receives the
 * tick's position, the unit it was derived in, and whether the short (phone) form is wanted.
 * [positions] places the ticks inside a range for a unit and step; without it ticks fall on
 * multiples of the step's size (see [tickPositions]).
 */
class TickScheme(
	val units: List<TickUnit>,
	val positions: ((range: LongRange, unit: TickUnit, step: Long) -> List<Long>)? = null,
	val format: (position: Long, unit: TickUnit, short: Boolean) -> String,
) {
	fun positionsIn(range: LongRange, unit: TickUnit, step: Long): List<Long> =
		positions?.invoke(range, unit, step) ?: tickPositions(range, unit, step)
}

/** Every distance is in dp. */
data class LayoutMetrics(
	val padTop: Float = 36f,
	val padBottom: Float = 20f,
	val padRight: Float = 10f,
	val axisWidth: Float = 92f,
	val axisWidthPhone: Float = 64f,
	val maxGap: Float = 56f,
	val coveredGapFactor: Float = 4f,
	val foldThreshold: Float = 1.5f,
	val minSpanHeight: Float = 8f,
	val bandMin: Float = 56f,
	val bandMax: Float = 140f,
	val bandLaneFraction: Float = .56f,
	val laneMinWidth: Float = 72f,
	/** Room a desktop lane keeps beside its band and bars for leader labels. */
	val laneLabelRoom: Float = 150f,
	/** Below this much label room a desktop lane's leader labels move to the ledger as callouts. */
	val minLeaderLabelWidth: Float = 72f,
	val barWidth: Float = 10f,
	val barStep: Float = 20f,
	val barOffset: Float = 6f,
	val railWidth: Float = 22f,
	val railBand: Float = 14f,
	val railBar: Float = 6f,
	val ledgerMin: Float = 140f,
	val ledgerMax: Float = 200f,
	val ledgerPad: Float = 44f,
	val ledgerLineHeight: Float = 17f,
	val ledgerGutter: Float = 14f,
	val leaderThreshold: Float = 6f,
	val sequenceRowStep: Float = 40f,
	val hitSize: Float = 16f,
	val targetTicks: Int = 8,
)

data class TimelineLayoutInput(
	val events: List<LayoutEvent>,
	val laneOrder: List<String>,
	val view: TimelineView,
	val frame: TimelineFrame,
	val widthDp: Float,
	val phone: Boolean,
	val ticks: TickScheme,
	val metrics: LayoutMetrics = LayoutMetrics(),
)
