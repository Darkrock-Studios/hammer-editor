package com.darkrockstudios.apps.hammer.common.timeline.layout

data class RectDp(val x: Float, val y: Float, val width: Float, val height: Float) {
	val right: Float get() = x + width
	val bottom: Float get() = y + height

	fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom

	fun intersects(other: RectDp): Boolean =
		x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

data class PointDp(val x: Float, val y: Float)

data class AxisTick(val yDp: Float, val label: String, val position: Long)

/** [xDp] is the axis line; tick labels end left of it. [foldGlyphYs] are the centres of the breaks. */
data class AxisLayout(val xDp: Float, val ticks: List<AxisTick>, val foldGlyphYs: List<Float>)

/** [header] is null on phone, where lanes are rails with only a cap in their colour. */
data class LaneColumn(
	val name: String,
	val index: Int,
	val xDp: Float,
	val widthDp: Float,
	val header: String?,
	val headerTruncated: Boolean,
)

/** A folded stretch of the axis; the break is drawn through its middle. */
data class FoldBand(val y0: Float, val y1: Float)

data class MilestoneRule(val eventId: Int, val yDp: Float)

data class BandShape(
	val eventId: Int,
	val laneIndex: Int,
	val rect: RectDp,
	/** Step of the lane colour's tonal ramp, so abutting segments always differ. */
	val shadeIndex: Int,
	val label: BandLabel,
	/** The span only reflects the precision of a single date. */
	val circa: Boolean,
)

sealed class BandLabel {
	/** Wrapped text inside the band; [small] uses [LabelStyle.BandLabelSmall]. */
	data class Lines(val lines: List<String>, val xDp: Float, val firstBaselineDp: Float, val small: Boolean) : BandLabel()

	/** One line rotated -90 degrees about its centre. */
	data class Rotated(val text: String, val cx: Float, val cy: Float) : BandLabel()

	/** The title did not fit inside, so it sits beside the band on a leader. */
	data class Leader(val label: LeaderLabel) : BandLabel()

	data object None : BandLabel()
}

data class BarShape(
	val eventId: Int,
	val laneIndex: Int,
	val rect: RectDp,
	/** Sub-column, from 1; column 0 is the band. */
	val column: Int,
	val label: LeaderLabel?,
	val circa: Boolean,
)

enum class PointGlyph { Diamond, MilestoneDiamond, EntryBox }

data class PointMark(
	val eventId: Int,
	val laneIndex: Int,
	val x: Float,
	val y: Float,
	val glyph: PointGlyph,
	val label: LeaderLabel?,
)

/** A label beside its mark inside the lane, joined by the [leader] polyline. */
data class LeaderLabel(
	val lines: List<String>,
	val sub: String?,
	val xDp: Float,
	val firstBaselineDp: Float,
	val leader: List<PointDp>,
	val fullText: String,
)

enum class LedgerStyle { Muted, Milestone, LaneCallout }

enum class LedgerMark { None, Pilcrow, MilestoneDiamond, SpanBar, Dot, LaneSwatch }

/**
 * One row of the ledger. [anchorYDp] is the moment the row stands for, [textYDp] the baseline of
 * its first line; [leader] joins the two when the row had to move.
 */
data class LedgerRow(
	val eventId: Int,
	val kind: LayoutKind,
	val anchorYDp: Float,
	val anchorY1Dp: Float?,
	val textYDp: Float,
	val lines: List<String>,
	val sub: String?,
	val style: LedgerStyle,
	val mark: LedgerMark,
	val laneIndex: Int?,
	val leader: List<PointDp>?,
	val hitRect: RectDp,
	val fullText: String,
)

/** The selected moment projected across the lanes; [rect] is the tint (zero height for a point). */
data class ProjectionOverlay(val eventId: Int, val yDp: Float, val y1Dp: Float?, val rect: RectDp)

/** Where an event sits on the drawing's time axis: its start, and its end when it spans. */
data class EventAnchor(val yDp: Float, val y1Dp: Float?)

sealed class HitTarget {
	data class Band(val eventId: Int) : HitTarget()
	data class Point(val eventId: Int) : HitTarget()
	data class Ledger(val eventId: Int) : HitTarget()
	/** A lane's header, or a phone rail's cap. */
	data class LaneHeader(val lane: String) : HitTarget()
}

data class HitRegion(val rect: RectDp, val target: HitTarget)

/**
 * Everything a renderer needs to draw a dated timeline, in dp. Lane colours are not resolved here:
 * the renderer maps a lane index to its palette.
 *
 * [hitRegions] are in draw order, so hit-test them from the end.
 */
data class TimelineLayout(
	val widthDp: Float,
	val heightDp: Float,
	val axis: AxisLayout,
	val lanes: List<LaneColumn>,
	val laneRegion: RectDp,
	val ledgerXDp: Float,
	val ledgerTextXDp: Float,
	val ledgerWidthDp: Float,
	val folds: List<FoldBand>,
	val milestoneRules: List<MilestoneRule>,
	val bands: List<BandShape>,
	val bars: List<BarShape>,
	val points: List<PointMark>,
	val ledgerRows: List<LedgerRow>,
	val projection: ProjectionOverlay?,
	val hitRegions: List<HitRegion>,
	/** Every drawn event's place on the axis, so any of them can be projected without a new layout. */
	val anchors: Map<Int, EventAnchor> = emptyMap(),
) {
	/** [eventId] projected across the lanes, as [projection] is for the input's selection. */
	fun projectionOf(eventId: Int?): ProjectionOverlay? {
		val anchor = eventId?.let(anchors::get) ?: return null
		val height = (anchor.y1Dp ?: anchor.yDp) - anchor.yDp
		return ProjectionOverlay(eventId, anchor.yDp, anchor.y1Dp, RectDp(laneRegion.x, anchor.yDp, laneRegion.width, height))
	}
}
