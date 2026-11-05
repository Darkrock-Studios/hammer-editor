package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.advanceStoryDate
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateTick
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.rangeEnd
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.storyDateAt
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.TickScheme
import com.darkrockstudios.apps.hammer.common.timeline.layout.TickUnit
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.common.timeline.layout.tickPositions

// Gregorian years and months vary in length; the mean only chooses the unit and step, ticks fall
// on the civil boundaries.
private const val GREGORIAN_YEAR_MINUTES = 525_949L
private const val GREGORIAN_MONTH_MINUTES = GREGORIAN_YEAR_MINUTES / 12
private const val TICK_LIMIT = 1000

/**
 * The layout input for a dated timeline's [rows]. Rows without a start that resolves on [calendar]
 * are left out (the overview already counts them as undated), as are archived scenes. A non-empty
 * [laneFilter] drops the events of every other lane; scenes and unlaned events stay in the ledger.
 * See [toLayoutEvent] for [entryMark].
 */
fun toLayoutInput(
	rows: List<TimelineRow>,
	lanes: List<LaneCount>,
	laneFilter: Set<String>,
	calendar: CalendarDef,
	view: TimelineView,
	widthDp: Float,
	phone: Boolean,
	frame: TimelineFrame = TimelineFrame.Everything,
	words: StoryDateWords = StoryDateWords.English,
	entryMark: String? = null,
): TimelineLayoutInput =
	TimelineLayoutInput(
		events = rows.mapNotNull { it.toLayoutEvent(calendar, laneFilter, entryMark) },
		laneOrder = lanes.map { it.lane },
		view = view,
		frame = frame,
		widthDp = widthDp,
		phone = phone,
		ticks = timelineTickScheme(calendar, words),
	)

/**
 * This row as the drawing lays it out, or null when the drawing leaves it out: it has no start that
 * resolves on [calendar], is an archived scene, or is in a lane [laneFilter] drops. A placed entry's
 * title leads with [entryMark] wherever it is written out, on a span or in the ledger; a point in a
 * lane is drawn in its own entry box and keeps its plain title.
 */
fun TimelineRow.toLayoutEvent(calendar: CalendarDef, laneFilter: Set<String>, entryMark: String? = null): LayoutEvent? {
	if (subjectArchived || !passesLaneFilter(laneFilter)) return null
	val startDate = start ?: return null
	val startPosition = StoryCalendar.position(calendar, startDate) ?: return null
	val endPosition = end?.let { rangeEnd(calendar, startPosition, it) }
	val laid = LayoutEvent(
		id = event.id,
		kind = event.kind.toLayoutKind(),
		title = title,
		subtitle = subtitle,
		lane = event.lane?.takeIf { it.isNotBlank() },
		milestone = event.milestone,
		order = event.order,
		start = startPosition,
		end = endPosition,
		precisionExtent = if (endPosition == null) precisionExtent(calendar, startDate) else 1L,
	)
	val boxedPoint = laid.lane != null && !laid.isSpan
	val marked = entryMark != null && event.kind == TimelineKind.ENTRY && !boxedPoint && title.isNotBlank()
	return if (marked) laid.copy(title = "$entryMark $title") else laid
}

/** How many minutes [start] covers: one when it is given to the minute, more when it stops at a coarser unit. */
internal fun precisionExtent(calendar: CalendarDef, start: StoryDate): Long =
	if (StoryCalendar.precision(calendar, start)?.name != StoryCalendar.MINUTE) {
		StoryCalendar.span(calendar, start)?.let { it.last - it.first + 1 } ?: 1L
	} else {
		1L
	}

private fun TimelineKind.toLayoutKind(): LayoutKind = when (this) {
	TimelineKind.EVENT -> LayoutKind.EVENT
	TimelineKind.ENTRY -> LayoutKind.ENTRY
	TimelineKind.SCENE -> LayoutKind.SCENE
}

// Clock units tick on the quarter and half hour, and on hours that divide a day.
private val MINUTE_STEPS = listOf(1L, 2L, 5L, 10L, 15L, 30L)
private val HOUR_STEPS = listOf(1L, 2L, 3L, 6L, 12L)

private fun clockSteps(unit: String): List<Long> = when (unit) {
	StoryCalendar.MINUTE -> MINUTE_STEPS
	StoryCalendar.HOUR -> HOUR_STEPS
	else -> emptyList()
}

/** Ticks in the units a date on [calendar] is entered in, coarsest first, labelled in [words]. */
fun timelineTickScheme(calendar: CalendarDef, words: StoryDateWords = StoryDateWords.English): TickScheme {
	val units = entryUnits(calendar).map { TickUnit(it.name, unitMinutes(calendar.preset, it.name), clockSteps(it.name)) }
	val positions: ((LongRange, TickUnit, Long) -> List<Long>)? = if (calendar.preset == CalendarPreset.GREGORIAN) {
		{ range, unit, step -> gregorianTickPositions(calendar, range, unit, step) }
	} else {
		null
	}
	return TickScheme(units, positions) { position, unit, short ->
		formatStoryDateTick(calendar, storyDateAt(calendar, position, unit.name), short, words)
	}
}

/**
 * The year or month starts inside [range] whose count from year 0 is a multiple of [step]; finer
 * units have a fixed length and tick on multiples of it.
 */
private fun gregorianTickPositions(calendar: CalendarDef, range: LongRange, unit: TickUnit, step: Long): List<Long> {
	val index: (StoryDate) -> Long = when (unit.name) {
		StoryCalendar.YEAR -> { date -> date[StoryCalendar.YEAR]!! }
		StoryCalendar.MONTH -> { date -> date[StoryCalendar.YEAR]!! * 12 + date[StoryCalendar.MONTH]!! - 1 }
		else -> return tickPositions(range, unit, step)
	}
	if (step <= 0) return emptyList()
	val truncated = storyDateAt(calendar, range.first, unit.name)
	var date = advanceStoryDate(calendar, truncated, unit.name, -index(truncated).mod(step))
	val out = ArrayList<Long>()
	while (out.size < TICK_LIMIT) {
		val p = StoryCalendar.position(calendar, date) ?: break
		if (p > range.last) break
		if (p >= range.first) out += p
		date = advanceStoryDate(calendar, date, unit.name, step)
	}
	return out
}

private fun unitMinutes(preset: CalendarPreset, unit: String): Long = when (unit) {
	StoryCalendar.MINUTE -> 1L
	StoryCalendar.HOUR -> 60L
	StoryCalendar.DAY -> StoryCalendar.MINUTES_PER_DAY
	StoryCalendar.MONTH -> GREGORIAN_MONTH_MINUTES
	else -> if (preset == CalendarPreset.GREGORIAN) GREGORIAN_YEAR_MINUTES else 365 * StoryCalendar.MINUTES_PER_DAY
}
