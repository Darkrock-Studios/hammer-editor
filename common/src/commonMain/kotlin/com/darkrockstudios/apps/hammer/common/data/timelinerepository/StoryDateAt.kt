package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate

/**
 * The date holding [position] on [calendar], truncated to [unit]: the inverse of
 * [StoryCalendar.position] for a date filled from the calendar's coarsest unit (see [entryUnits])
 * down to [unit]. A [unit] coarser than that is read as the coarsest.
 */
fun storyDateAt(calendar: CalendarDef, position: Long, unit: String): StoryDate {
	require(StoryCalendar.units(calendar.preset).any { it.name == unit }) { "$unit is not a unit of ${calendar.preset}" }
	return when (calendar.preset) {
		CalendarPreset.STORY -> storyDateAt(entryUnits(calendar).map { it.name to it.perParent }, position, unit)
		CalendarPreset.GREGORIAN -> {
			val day = gregorianDate(position, unit)
			when (unit) {
				StoryCalendar.YEAR -> StoryDate(mapOf(StoryCalendar.YEAR to day[StoryCalendar.YEAR]!!))
				StoryCalendar.MONTH -> StoryDate(
					linkedMapOf(
						StoryCalendar.YEAR to day[StoryCalendar.YEAR]!!,
						StoryCalendar.MONTH to day[StoryCalendar.MONTH]!!,
					)
				)

				else -> day
			}
		}
	}
}

/**
 * Where a range closed by [end] stops: the end of [end]'s span, so "year 5" closes when year 5 does.
 * A minute-precise end is an instant and stops where it starts. Null when [end] is invalid.
 */
fun rangeEnd(calendar: CalendarDef, end: StoryDate): Long? {
	val span = StoryCalendar.span(calendar, end) ?: return null
	return if (StoryCalendar.precision(calendar, end)?.name == StoryCalendar.MINUTE) span.first else span.last + 1
}

/**
 * Whether [start] to [end] is a single moment on [calendar]: both are valid and [end]'s [rangeEnd]
 * is not past [start], as when a range starts and ends on the same minute. The drawing shows such a
 * range as a point.
 */
fun isMomentRange(calendar: CalendarDef, start: StoryDate, end: StoryDate): Boolean {
	val startAt = StoryCalendar.position(calendar, start) ?: return false
	val stop = rangeEnd(calendar, end) ?: return false
	return stop <= startAt
}

private fun storyDateAt(chain: List<Pair<String, Long?>>, position: Long, unit: String): StoryDate {
	val last = chain.indexOfFirst { it.first == unit }.coerceAtLeast(0)
	// Minutes in one of each unit, finest last.
	val minutes = LongArray(chain.size)
	var size = 1L
	for (i in chain.indices.reversed()) {
		minutes[i] = size
		chain[i].second?.let { size *= it }
	}
	val rungs = LinkedHashMap<String, Long>()
	rungs[chain[0].first] = position.floorDiv(minutes[0])
	for (i in 1..last) {
		rungs[chain[i].first] = position.floorDiv(minutes[i]).mod(chain[i].second!!)
	}
	return StoryDate(rungs)
}
