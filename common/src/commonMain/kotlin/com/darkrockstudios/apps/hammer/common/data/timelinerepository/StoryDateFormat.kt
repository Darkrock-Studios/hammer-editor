package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate

/** The words a [StoryDate] is written with, so the UI can supply translated ones. */
interface StoryDateWords {
	/** A unit's name as it heads a value, e.g. `Year`. */
	fun unit(unit: String): String

	/** 1 is January. */
	fun month(month: Int): String

	/** [text] placed before the epoch named [epoch], e.g. `Year 3 before Landing`. */
	fun beforeEpoch(text: String, epoch: String): String

	/** A Gregorian year before year 1, given as its BCE number (astronomical year 0 is 1 BCE). */
	fun bce(year: Long): String

	companion object {
		val English: StoryDateWords = object : StoryDateWords {
			private val months = listOf(
				"January", "February", "March", "April", "May", "June",
				"July", "August", "September", "October", "November", "December",
			)

			override fun unit(unit: String): String = unit.replaceFirstChar { it.uppercaseChar() }
			override fun month(month: Int): String = months.getOrElse(month - 1) { month.toString() }
			override fun beforeEpoch(text: String, epoch: String): String = "$text before $epoch"
			override fun bce(year: Long): String = "$year BCE"
		}
	}
}

/**
 * The units a date on [calendar] is entered in: from the coarsest it is told in (a STORY calendar's
 * [CalendarDef.coarsestUnit], else the top unit) down to the minute.
 */
fun entryUnits(calendar: CalendarDef): List<CalendarUnit> {
	val chain = StoryCalendar.units(calendar.preset)
	val coarsest = calendar.coarsestUnit
		?.takeIf { calendar.preset == CalendarPreset.STORY }
		?.let { unit -> chain.indexOfFirst { it.name == unit } }
		?.takeIf { it >= 0 }
		?: 0
	return chain.drop(coarsest)
}

private const val SEPARATOR = " · "

/**
 * A reader-facing form of [date]: `Year 12 · Day 40 · 06:30` on a STORY calendar (the coarsest rung
 * named before the epoch when negative and the calendar has one), `12 May 1802 14:30` on a GREGORIAN
 * one. A date that does not fit [calendar] falls back to [toCompactText].
 */
fun formatStoryDate(
	calendar: CalendarDef,
	date: StoryDate,
	words: StoryDateWords = StoryDateWords.English,
): String {
	if (StoryCalendar.validate(calendar, date) != null) return date.toCompactText()
	return when (calendar.preset) {
		CalendarPreset.STORY -> formatStory(calendar, date, words)
		CalendarPreset.GREGORIAN -> formatGregorian(date, words)
	}
}

/** `start → end`, or just the start when there is no end. */
fun formatStoryDateRange(
	calendar: CalendarDef,
	start: StoryDate,
	end: StoryDate?,
	words: StoryDateWords = StoryDateWords.English,
): String {
	val from = formatStoryDate(calendar, start, words)
	return if (end == null) from else "$from → ${formatStoryDate(calendar, end, words)}"
}

private fun formatStory(calendar: CalendarDef, date: StoryDate, words: StoryDateWords): String {
	val filled = StoryCalendar.units(calendar.preset).map { it.name }.filter { date[it] != null }
	val leading = filled.first()
	val epoch = calendar.epochName?.trim()?.takeIf { it.isNotEmpty() }

	val parts = mutableListOf<String>()
	for (unit in filled) {
		val value = date[unit]!!
		when {
			// A bounded hour with its minute reads as a clock time.
			unit == StoryCalendar.HOUR && unit != leading && date[StoryCalendar.MINUTE] != null ->
				parts += clock(value, date[StoryCalendar.MINUTE]!!)

			unit == StoryCalendar.MINUTE && StoryCalendar.HOUR in filled && leading != StoryCalendar.HOUR -> Unit

			unit == leading && value < 0 && epoch != null ->
				parts += words.beforeEpoch("${words.unit(unit)} ${-value}", epoch)

			else -> parts += "${words.unit(unit)} $value"
		}
	}
	return parts.joinToString(SEPARATOR)
}

private fun formatGregorian(date: StoryDate, words: StoryDateWords): String {
	val year = date[StoryCalendar.YEAR]!!
	val yearText = if (year > 0) year.toString() else words.bce(1 - year)
	val month = date[StoryCalendar.MONTH]
	val day = date[StoryCalendar.DAY]
	val hour = date[StoryCalendar.HOUR]

	val calendarDay = when {
		month == null -> yearText
		day == null -> "${words.month(month.toInt())} $yearText"
		else -> "$day ${words.month(month.toInt())} $yearText"
	}
	if (hour == null) return calendarDay
	return "$calendarDay ${clock(hour, date[StoryCalendar.MINUTE] ?: 0)}"
}

private fun clock(hour: Long, minute: Long): String =
	"${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
