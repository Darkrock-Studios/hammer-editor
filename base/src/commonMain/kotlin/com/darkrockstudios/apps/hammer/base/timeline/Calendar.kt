package com.darkrockstudios.apps.hammer.base.timeline

import kotlinx.serialization.Serializable

/** [code] is hashed; never reuse or renumber one. */
@Serializable
enum class CalendarPreset(val code: Int) {
	/** year / day / hour / minute from a named epoch; negative years fall before it. */
	STORY(0),

	/** Proleptic Gregorian year / month / day / hour / minute; year 0 is 1 BCE. */
	GREGORIAN(1),
}

@Serializable
data class CalendarDef(
	val preset: CalendarPreset = CalendarPreset.STORY,
	val epochName: String? = null,
	/**
	 * The coarsest unit the story is told in, so a one-day story can run in hours and minutes. Dates
	 * may omit the units above it.
	 */
	val coarsestUnit: String? = null,
)

/** [perParent] is how many of this unit make one of the unit above; null for the top unit. */
data class CalendarUnit(val name: String, val perParent: Long?)

sealed interface StoryDateProblem {
	data class UnknownUnit(val unit: String) : StoryDateProblem

	/** The filled rungs are not a contiguous run down the unit chain from an allowed top. */
	data object NotAPrefix : StoryDateProblem

	data class OutOfRange(val unit: String, val value: Long) : StoryDateProblem
}

/**
 * Validation and arithmetic for [StoryDate]s. Positions are minutes from the epoch (STORY) or from
 * 1970-01-01 (GREGORIAN).
 *
 * A date must fill a contiguous run of the unit chain. The run starts at the top unit, or, when the
 * calendar has a [CalendarDef.coarsestUnit], at any unit down to it; omitted leading units take
 * their lowest value (STORY 0; GREGORIAN year 0, month 1, day 1). In STORY a date that omits the
 * leading units has an unbounded first rung, like year, so "hour 30" or "hour -2" are valid in a
 * story told in hours. GREGORIAN always applies its month and day ranges.
 */
object StoryCalendar {
	const val YEAR = "year"
	const val MONTH = "month"
	const val DAY = "day"
	const val HOUR = "hour"
	const val MINUTE = "minute"

	/** Bound on the year magnitude so every position fits in a Long with room to spare. */
	const val MAX_YEAR = 1_000_000_000L

	private val storyUnits = listOf(
		CalendarUnit(YEAR, null),
		CalendarUnit(DAY, 365),
		CalendarUnit(HOUR, 24),
		CalendarUnit(MINUTE, 60),
	)

	private val gregorianUnits = listOf(
		CalendarUnit(YEAR, null),
		CalendarUnit(MONTH, 12),
		// Nominal: the real bound depends on the month and year.
		CalendarUnit(DAY, 31),
		CalendarUnit(HOUR, 24),
		CalendarUnit(MINUTE, 60),
	)

	private const val MINUTES_PER_DAY = 24L * 60L

	fun units(preset: CalendarPreset): List<CalendarUnit> = when (preset) {
		CalendarPreset.STORY -> storyUnits
		CalendarPreset.GREGORIAN -> gregorianUnits
	}

	fun validate(preset: CalendarPreset, date: StoryDate): StoryDateProblem? =
		validate(CalendarDef(preset), date)

	fun validate(calendar: CalendarDef, date: StoryDate): StoryDateProblem? =
		resolve(calendar, date).problem

	/** The finest filled unit, or null when [date] is invalid. */
	fun precision(preset: CalendarPreset, date: StoryDate): CalendarUnit? =
		precision(CalendarDef(preset), date)

	fun precision(calendar: CalendarDef, date: StoryDate): CalendarUnit? {
		val resolved = resolve(calendar, date).resolved ?: return null
		return units(calendar.preset)[resolved.finest]
	}

	/** Minutes at the start of [date], or null when it is invalid. */
	fun position(preset: CalendarPreset, date: StoryDate): Long? = position(CalendarDef(preset), date)

	fun position(calendar: CalendarDef, date: StoryDate): Long? {
		val resolved = resolve(calendar, date).resolved ?: return null
		return when (calendar.preset) {
			CalendarPreset.STORY -> storyPosition(resolved.values)
			CalendarPreset.GREGORIAN -> gregorianPosition(resolved.values)
		}
	}

	/**
	 * The minutes [date] covers: from its position up to, not including, the position of the next
	 * value of its finest unit. Null when it is invalid.
	 */
	fun span(preset: CalendarPreset, date: StoryDate): LongRange? = span(CalendarDef(preset), date)

	fun span(calendar: CalendarDef, date: StoryDate): LongRange? {
		val resolved = resolve(calendar, date).resolved ?: return null
		val values = resolved.values
		return when (calendar.preset) {
			CalendarPreset.STORY -> {
				val start = storyPosition(values)
				start until start + storyMinutesPer(resolved.finest)
			}

			CalendarPreset.GREGORIAN -> {
				val start = gregorianPosition(values)
				val end = when (gregorianUnits[resolved.finest].name) {
					YEAR -> GregorianArithmetic.daysFromCivil(values[0] + 1, 1, 1) * MINUTES_PER_DAY
					MONTH -> {
						val month = values[1].toInt()
						val nextYear = if (month == 12) values[0] + 1 else values[0]
						val nextMonth = if (month == 12) 1 else month + 1
						GregorianArithmetic.daysFromCivil(nextYear, nextMonth, 1) * MINUTES_PER_DAY
					}

					DAY -> start + MINUTES_PER_DAY
					HOUR -> start + 60
					else -> start + 1
				}
				start until end
			}
		}
	}

	/** A full value per chain unit (omitted units at their lowest) and the finest filled index. */
	private class Resolved(val values: LongArray, val finest: Int)

	private class Resolution(val resolved: Resolved?, val problem: StoryDateProblem?)

	private fun invalid(problem: StoryDateProblem) = Resolution(null, problem)

	private fun resolve(calendar: CalendarDef, date: StoryDate): Resolution {
		val chain = units(calendar.preset)
		val indexOf = chain.withIndex().associate { (i, unit) -> unit.name to i }

		date.rungs.keys.sorted().forEach { key ->
			if (key !in indexOf) return invalid(StoryDateProblem.UnknownUnit(key))
		}

		val indices = date.rungs.keys.map { indexOf.getValue(it) }.sorted()
		val first = indices.first()
		val finest = indices.last()
		val coarsestAllowed = calendar.coarsestUnit?.let { indexOf[it] } ?: 0
		if (first > coarsestAllowed || indices != (first..finest).toList()) {
			return invalid(StoryDateProblem.NotAPrefix)
		}

		val values = LongArray(chain.size) { i ->
			date[chain[i].name] ?: lowestValue(calendar.preset, chain[i].name)
		}

		for (i in first..finest) {
			val unit = chain[i]
			val value = values[i]
			val inRange = when {
				i == 0 -> value in -MAX_YEAR..MAX_YEAR
				calendar.preset == CalendarPreset.STORY && i == first -> {
					val bound = MAX_YEAR * storyMinutesPer(0) / storyMinutesPer(i)
					value in -bound..bound
				}

				calendar.preset == CalendarPreset.STORY -> value in 0 until unit.perParent!!
				else -> gregorianInRange(unit.name, value, values)
			}
			if (!inRange) return invalid(StoryDateProblem.OutOfRange(unit.name, value))
		}

		return Resolution(Resolved(values, finest), null)
	}

	private fun lowestValue(preset: CalendarPreset, unit: String): Long = when {
		preset == CalendarPreset.GREGORIAN && (unit == MONTH || unit == DAY) -> 1
		else -> 0
	}

	private fun gregorianInRange(unit: String, value: Long, values: LongArray): Boolean = when (unit) {
		MONTH -> value in 1..12
		DAY -> {
			val month = values[1]
			// An out-of-range month is reported on its own rung first; any bound will do here.
			val days = if (month in 1..12) GregorianArithmetic.daysInMonth(values[0], month.toInt()) else 31
			value in 1..days.toLong()
		}

		HOUR -> value in 0..23
		else -> value in 0..59
	}

	private fun storyMinutesPer(index: Int): Long {
		var minutes = 1L
		for (i in index + 1 until storyUnits.size) {
			minutes *= storyUnits[i].perParent!!
		}
		return minutes
	}

	private fun storyPosition(values: LongArray): Long =
		values.indices.sumOf { i -> values[i] * storyMinutesPer(i) }

	private fun gregorianPosition(values: LongArray): Long {
		val days = GregorianArithmetic.daysFromCivil(values[0], values[1].toInt(), values[2].toInt())
		return days * MINUTES_PER_DAY + values[3] * 60 + values[4]
	}
}
