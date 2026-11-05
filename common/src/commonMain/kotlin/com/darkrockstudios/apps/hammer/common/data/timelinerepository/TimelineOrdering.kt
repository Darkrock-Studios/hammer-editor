package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode

private val manualOrder = compareBy<TimeLineEvent>({ it.order }, { it.id })

/**
 * The order a timeline shows its events in. MANUAL follows [TimeLineEvent.order]. DATED follows the
 * start date's position, undated (or invalid) starts last, ties broken by [TimeLineEvent.order].
 */
fun displayOrder(events: List<TimeLineEvent>, mode: TimelineMode, calendar: CalendarDef?): List<TimeLineEvent> =
	when (mode) {
		TimelineMode.MANUAL -> events.sortedWith(manualOrder)
		TimelineMode.DATED -> {
			val cal = calendar ?: CalendarDef()
			events
				.map { event -> event to event.start?.let { StoryCalendar.position(cal, it) } }
				.sortedWith(
					compareBy<Pair<TimeLineEvent, Long?>, Long?>(nullsLast()) { it.second }
						.thenBy(manualOrder) { it.first }
				)
				.map { it.first }
		}
	}

/** Dates for "space evenly": the i-th event lands [step] × i [unit]s after [origin]. */
data class EvenSpacing(val unit: String, val step: Long = 1, val origin: StoryDate)

/**
 * A start date per event id, in [ordered] order. Each date has rungs down to [EvenSpacing.unit],
 * finer rungs of the origin are dropped.
 */
fun spaceEvenly(ordered: List<TimeLineEvent>, calendar: CalendarDef, spacing: EvenSpacing): Map<Int, StoryDate> =
	ordered.withIndex().associate { (index, event) ->
		event.id to advanceStoryDate(calendar, spacing.origin, spacing.unit, index * spacing.step)
	}

/** Every event's index in [ordered], to store as its manual order. */
fun freezeOrder(ordered: List<TimeLineEvent>): Map<Int, Int> =
	ordered.withIndex().associate { (index, event) -> event.id to index }

/**
 * [origin] truncated to [unit] and moved by [amount] of it, carrying into coarser rungs. A STORY
 * result starts at the origin's coarsest rung, or at [unit] when that is coarser.
 */
internal fun advanceStoryDate(calendar: CalendarDef, origin: StoryDate, unit: String, amount: Long): StoryDate {
	require(StoryCalendar.validate(calendar, origin) == null) { "Not a valid origin: $origin" }
	val chain = StoryCalendar.units(calendar.preset)
	val unitIndex = chain.indexOfFirst { it.name == unit }
	require(unitIndex >= 0) { "$unit is not a unit of ${calendar.preset}" }

	return when (calendar.preset) {
		CalendarPreset.STORY -> {
			val originFirst = origin.rungs.keys.minOf { key -> chain.indexOfFirst { it.name == key } }
			advanceStory(chain, origin, minOf(originFirst, unitIndex), unitIndex, amount)
		}

		CalendarPreset.GREGORIAN -> advanceGregorian(origin, chain[unitIndex].name, amount)
	}
}

private fun advanceStory(
	chain: List<CalendarUnit>,
	origin: StoryDate,
	first: Int,
	last: Int,
	amount: Long,
): StoryDate {
	var count = 0L
	for (i in first..last) {
		val per = if (i == first) 1L else chain[i].perParent!!
		count = count * per + (origin[chain[i].name] ?: 0L)
	}
	count += amount

	val values = LongArray(last - first + 1)
	for (i in last downTo first + 1) {
		val per = chain[i].perParent!!
		values[i - first] = count.mod(per)
		count = count.floorDiv(per)
	}
	values[0] = count

	return StoryDate((first..last).associateTo(LinkedHashMap()) { chain[it].name to values[it - first] })
}

private fun advanceGregorian(origin: StoryDate, unit: String, amount: Long): StoryDate {
	val year = origin[StoryCalendar.YEAR]!!
	val month = origin[StoryCalendar.MONTH] ?: 1L
	return when (unit) {
		StoryCalendar.YEAR -> StoryDate(mapOf(StoryCalendar.YEAR to year + amount))
		StoryCalendar.MONTH -> {
			val months = year * 12 + (month - 1) + amount
			StoryDate(
				linkedMapOf(
					StoryCalendar.YEAR to months.floorDiv(12L),
					StoryCalendar.MONTH to months.mod(12L) + 1,
				)
			)
		}

		else -> {
			val truncated = StoryDate(
				linkedMapOf(
					StoryCalendar.YEAR to year,
					StoryCalendar.MONTH to month,
					StoryCalendar.DAY to (origin[StoryCalendar.DAY] ?: 1L),
					StoryCalendar.HOUR to (origin[StoryCalendar.HOUR] ?: 0L),
					StoryCalendar.MINUTE to (origin[StoryCalendar.MINUTE] ?: 0L),
				)
			)
			val unitMinutes = when (unit) {
				StoryCalendar.DAY -> StoryCalendar.MINUTES_PER_DAY
				StoryCalendar.HOUR -> 60L
				else -> 1L
			}
			val start = StoryCalendar.position(CalendarPreset.GREGORIAN, truncated)!!
			// Drop the rungs finer than the unit before moving, so the origin is truncated.
			val minutes = start - start.mod(unitMinutes) + amount * unitMinutes
			gregorianDate(minutes, unit)
		}
	}
}

/** The Gregorian date at [minutes] from 1970-01-01, with rungs down to [finest] but at least the day. */
internal fun gregorianDate(minutes: Long, finest: String): StoryDate {
	val days = minutes.floorDiv(StoryCalendar.MINUTES_PER_DAY)
	val ofDay = minutes.mod(StoryCalendar.MINUTES_PER_DAY)

	// Howard Hinnant's civil_from_days.
	val z = days + 719468
	val era = (if (z >= 0) z else z - 146096) / 146097
	val dayOfEra = z - era * 146097
	val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
	val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
	val monthFromMarch = (5 * dayOfYear + 2) / 153
	val day = dayOfYear - (153 * monthFromMarch + 2) / 5 + 1
	val month = if (monthFromMarch < 10) monthFromMarch + 3 else monthFromMarch - 9
	val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0

	val rungs = linkedMapOf(StoryCalendar.YEAR to year, StoryCalendar.MONTH to month, StoryCalendar.DAY to day)
	if (finest == StoryCalendar.HOUR || finest == StoryCalendar.MINUTE) rungs[StoryCalendar.HOUR] = ofDay / 60
	if (finest == StoryCalendar.MINUTE) rungs[StoryCalendar.MINUTE] = ofDay % 60
	return StoryDate(rungs)
}
