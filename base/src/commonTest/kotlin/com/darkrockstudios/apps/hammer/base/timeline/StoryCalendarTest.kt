package com.darkrockstudios.apps.hammer.base.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset.GREGORIAN
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset.STORY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryCalendarTest {
	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	private fun LongRange.length() = last - first + 1

	private val minutesPerDay = 24L * 60
	private val minutesPerStoryYear = 365L * minutesPerDay

	// ---- StoryDate -------------------------------------------------------------------------

	@Test
	fun `StoryDate needs at least one rung`() {
		assertFailsWith<IllegalArgumentException> { StoryDate(emptyMap()) }
	}

	@Test
	fun `StoryDate get reads a rung`() {
		val date = date("year" to 97, "day" to 210)
		assertEquals(210L, date["day"])
		assertNull(date["hour"])
	}

	// ---- Units -----------------------------------------------------------------------------

	@Test
	fun `unit chains`() {
		assertEquals(
			listOf(
				CalendarUnit("year", null),
				CalendarUnit("day", 365),
				CalendarUnit("hour", 24),
				CalendarUnit("minute", 60),
			),
			StoryCalendar.units(STORY),
		)
		assertEquals(
			listOf("year", "month", "day", "hour", "minute"),
			StoryCalendar.units(GREGORIAN).map { it.name },
		)
		assertEquals(12L, StoryCalendar.units(GREGORIAN)[1].perParent)
	}

	// ---- STORY -----------------------------------------------------------------------------

	@Test
	fun `story position follows the rung formula`() {
		val position = StoryCalendar.position(STORY, date("year" to 97, "day" to 210, "hour" to 23, "minute" to 5))
		assertEquals(((97L * 365 + 210) * 24 + 23) * 60 + 5, position)
	}

	@Test
	fun `story missing finer rungs count as zero`() {
		assertEquals(97L * minutesPerStoryYear, StoryCalendar.position(STORY, date("year" to 97)))
		assertEquals(
			StoryCalendar.position(STORY, date("year" to 97, "day" to 210, "hour" to 0, "minute" to 0)),
			StoryCalendar.position(STORY, date("year" to 97, "day" to 210)),
		)
	}

	@Test
	fun `story epoch is position zero`() {
		assertEquals(0L, StoryCalendar.position(STORY, date("year" to 0)))
	}

	@Test
	fun `story spans cover the finest filled unit`() {
		val yearStart = 97L * minutesPerStoryYear
		assertEquals(yearStart until yearStart + minutesPerStoryYear, StoryCalendar.span(STORY, date("year" to 97)))

		val dayStart = yearStart + 210 * minutesPerDay
		assertEquals(dayStart until dayStart + minutesPerDay, StoryCalendar.span(STORY, date("year" to 97, "day" to 210)))

		val hourStart = dayStart + 23 * 60
		assertEquals(
			hourStart until hourStart + 60,
			StoryCalendar.span(STORY, date("year" to 97, "day" to 210, "hour" to 23)),
		)
		assertEquals(
			hourStart + 59 until hourStart + 60,
			StoryCalendar.span(STORY, date("year" to 97, "day" to 210, "hour" to 23, "minute" to 59)),
		)
	}

	@Test
	fun `story last day of a year ends where the next year starts`() {
		val lastDay = StoryCalendar.span(STORY, date("year" to 3, "day" to 364))!!
		assertEquals(StoryCalendar.position(STORY, date("year" to 4)), lastDay.last + 1)
	}

	@Test
	fun `an end fits when its span reaches the start`() {
		val start = date("year" to 5, "day" to 10)
		assertTrue(StoryCalendar.endFits(CalendarDef(STORY), start, date("year" to 5)))
		assertTrue(StoryCalendar.endFits(CalendarDef(STORY), start, date("year" to 5, "day" to 10)))
		assertFalse(StoryCalendar.endFits(CalendarDef(STORY), start, date("year" to 5, "day" to 9)))
		assertFalse(StoryCalendar.endFits(CalendarDef(STORY), start, date("year" to 4)))
	}

	@Test
	fun `a gregorian end fits when its span reaches the start`() {
		val gregorian = CalendarDef(GREGORIAN)
		val start = date("year" to 1850, "month" to 3, "day" to 14)
		assertTrue(StoryCalendar.endFits(gregorian, start, date("year" to 1850, "month" to 3)))
		assertTrue(StoryCalendar.endFits(gregorian, start, date("year" to 1850)))
		assertFalse(StoryCalendar.endFits(gregorian, start, date("year" to 1850, "month" to 2)))
		assertFalse(StoryCalendar.endFits(gregorian, start, date("year" to 1850, "month" to 3, "day" to 13)))
	}

	@Test
	fun `a gregorian end before the common era fits by the same rule`() {
		val gregorian = CalendarDef(GREGORIAN)
		// Year -1 is 2 BCE.
		val start = date("year" to -1, "month" to 6, "day" to 1)
		assertTrue(StoryCalendar.endFits(gregorian, start, date("year" to -1)))
		assertTrue(StoryCalendar.endFits(gregorian, start, date("year" to 0)))
		assertFalse(StoryCalendar.endFits(gregorian, start, date("year" to -2)))
		assertFalse(StoryCalendar.endFits(gregorian, start, date("year" to -1, "month" to 5)))
	}

	@Test
	fun `a range reports an invalid start then an invalid end then an end before its start`() {
		val calendar = CalendarDef(STORY)
		val bad = date("day" to 3)
		assertEquals(
			TimelineDateProblem.Invalid(TimelineDateField.START, StoryDateProblem.NotAPrefix),
			StoryCalendar.rangeProblem(calendar, bad, bad),
		)
		assertEquals(
			TimelineDateProblem.Invalid(TimelineDateField.END, StoryDateProblem.NotAPrefix),
			StoryCalendar.rangeProblem(calendar, date("year" to 5), bad),
		)
		assertEquals(
			TimelineDateProblem.Invalid(TimelineDateField.END, StoryDateProblem.NotAPrefix),
			StoryCalendar.rangeProblem(calendar, null, bad),
		)
		assertEquals(
			TimelineDateProblem.EndBeforeStart,
			StoryCalendar.rangeProblem(calendar, date("year" to 5, "day" to 10), date("year" to 4)),
		)
		assertNull(StoryCalendar.rangeProblem(calendar, date("year" to 5, "day" to 10), date("year" to 5)))
		assertNull(StoryCalendar.rangeProblem(calendar, null, null))
	}

	@Test
	fun `an invalid date never fits`() {
		assertFalse(StoryCalendar.endFits(CalendarDef(STORY), date("day" to 3), date("year" to 5)))
		assertFalse(StoryCalendar.endFits(CalendarDef(STORY), date("year" to 5), date("day" to 3)))
	}

	@Test
	fun `story negative years fall before the epoch`() {
		assertEquals(-minutesPerStoryYear, StoryCalendar.position(STORY, date("year" to -1)))
		assertEquals(-minutesPerStoryYear until 0L, StoryCalendar.span(STORY, date("year" to -1)))
		assertEquals(-minutesPerDay, StoryCalendar.position(STORY, date("year" to -1, "day" to 364)))
	}

	@Test
	fun `story accepts a full chain and every prefix`() {
		assertNull(StoryCalendar.validate(STORY, date("year" to 1)))
		assertNull(StoryCalendar.validate(STORY, date("year" to 1, "day" to 0)))
		assertNull(StoryCalendar.validate(STORY, date("year" to 1, "day" to 364, "hour" to 23)))
		assertNull(StoryCalendar.validate(STORY, date("year" to 1, "day" to 364, "hour" to 23, "minute" to 59)))
	}

	@Test
	fun `rung insertion order does not matter`() {
		val reversed = date("minute" to 5, "hour" to 23, "day" to 210, "year" to 97)
		assertNull(StoryCalendar.validate(STORY, reversed))
		assertEquals(
			StoryCalendar.position(STORY, date("year" to 97, "day" to 210, "hour" to 23, "minute" to 5)),
			StoryCalendar.position(STORY, reversed),
		)
	}

	@Test
	fun `story rejects a date that is not a prefix`() {
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(STORY, date("day" to 3)))
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(STORY, date("year" to 1, "hour" to 3)))
		assertEquals(
			StoryDateProblem.NotAPrefix,
			StoryCalendar.validate(STORY, date("year" to 1, "day" to 2, "minute" to 3)),
		)
		assertNull(StoryCalendar.position(STORY, date("day" to 3)))
		assertNull(StoryCalendar.span(STORY, date("day" to 3)))
		assertNull(StoryCalendar.precision(STORY, date("day" to 3)))
	}

	@Test
	fun `story rejects an unknown unit`() {
		assertEquals(
			StoryDateProblem.UnknownUnit("month"),
			StoryCalendar.validate(STORY, date("year" to 1, "month" to 2)),
		)
	}

	@Test
	fun `story rejects rungs out of range`() {
		assertEquals(StoryDateProblem.OutOfRange("day", 365), StoryCalendar.validate(STORY, date("year" to 1, "day" to 365)))
		assertEquals(StoryDateProblem.OutOfRange("day", -1), StoryCalendar.validate(STORY, date("year" to 1, "day" to -1)))
		assertEquals(
			StoryDateProblem.OutOfRange("hour", 24),
			StoryCalendar.validate(STORY, date("year" to 1, "day" to 0, "hour" to 24)),
		)
		assertEquals(
			StoryDateProblem.OutOfRange("minute", 60),
			StoryCalendar.validate(STORY, date("year" to 1, "day" to 0, "hour" to 0, "minute" to 60)),
		)
		assertEquals(
			StoryDateProblem.OutOfRange("year", StoryCalendar.MAX_YEAR + 1),
			StoryCalendar.validate(STORY, date("year" to StoryCalendar.MAX_YEAR + 1)),
		)
		assertNull(StoryCalendar.validate(STORY, date("year" to -StoryCalendar.MAX_YEAR)))
	}

	@Test
	fun `precision is the finest filled unit`() {
		assertEquals("year", StoryCalendar.precision(STORY, date("year" to 1))?.name)
		assertEquals("day", StoryCalendar.precision(STORY, date("year" to 97, "day" to 210))?.name)
		assertEquals("minute", StoryCalendar.precision(GREGORIAN, date("year" to 2024, "month" to 1, "day" to 1, "hour" to 0, "minute" to 0))?.name)
	}

	// ---- coarsestUnit ----------------------------------------------------------------------

	private val hourStory = CalendarDef(preset = STORY, epochName = "The Fall", coarsestUnit = "hour")

	@Test
	fun `coarsestUnit lets a date start at that unit`() {
		assertNull(StoryCalendar.validate(hourStory, date("hour" to 5, "minute" to 30)))
		assertEquals(5L * 60 + 30, StoryCalendar.position(hourStory, date("hour" to 5, "minute" to 30)))
		assertEquals(330L until 331L, StoryCalendar.span(hourStory, date("hour" to 5, "minute" to 30)))
		assertEquals(300L until 360L, StoryCalendar.span(hourStory, date("hour" to 5)))
	}

	@Test
	fun `coarsestUnit is ignored by the preset overloads`() {
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(STORY, date("hour" to 5)))
		assertNull(StoryCalendar.position(STORY, date("hour" to 5)))
	}

	@Test
	fun `coarsestUnit still accepts dates that start higher up`() {
		val full = date("year" to 1, "day" to 2, "hour" to 3)
		assertNull(StoryCalendar.validate(hourStory, full))
		assertEquals(StoryCalendar.position(STORY, full), StoryCalendar.position(hourStory, full))
		assertNull(StoryCalendar.validate(hourStory, date("day" to 2, "hour" to 3)))
		assertEquals(
			StoryCalendar.position(STORY, date("year" to 0, "day" to 2, "hour" to 3)),
			StoryCalendar.position(hourStory, date("day" to 2, "hour" to 3)),
		)
	}

	@Test
	fun `coarsestUnit does not allow starting below it`() {
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(hourStory, date("minute" to 5)))
	}

	@Test
	fun `story leading rung below year is unbounded in both directions`() {
		assertNull(StoryCalendar.validate(hourStory, date("hour" to 30)))
		assertEquals(30L * 60, StoryCalendar.position(hourStory, date("hour" to 30)))
		assertEquals(-120L, StoryCalendar.position(hourStory, date("hour" to -2)))
		assertEquals(
			StoryDateProblem.OutOfRange("minute", 60),
			StoryCalendar.validate(hourStory, date("hour" to 5, "minute" to 60)),
		)
	}

	@Test
	fun `unknown coarsestUnit is a calendar problem`() {
		val odd = CalendarDef(preset = STORY, coarsestUnit = "fortnight")
		assertEquals(StoryDateProblem.UnknownCoarsestUnit("fortnight"), StoryCalendar.validate(odd))
		assertEquals(StoryDateProblem.UnknownCoarsestUnit("fortnight"), StoryCalendar.validate(odd, date("hour" to 5)))
		assertEquals(StoryDateProblem.UnknownCoarsestUnit("fortnight"), StoryCalendar.validate(odd, date("year" to 5)))
		assertNull(StoryCalendar.position(odd, date("year" to 5)))
		assertNull(StoryCalendar.span(odd, date("year" to 5)))
		assertNull(StoryCalendar.precision(odd, date("year" to 5)))
	}

	@Test
	fun `story calendars with a known or no coarsestUnit are valid`() {
		assertNull(StoryCalendar.validate(CalendarDef(preset = STORY)))
		assertNull(StoryCalendar.validate(hourStory))
		assertNull(StoryCalendar.validate(CalendarDef(preset = STORY, coarsestUnit = "year")))
	}

	// ---- GREGORIAN -------------------------------------------------------------------------

	@Test
	fun `gregorian unix epoch is position zero`() {
		assertEquals(0L, StoryCalendar.position(GREGORIAN, date("year" to 1970, "month" to 1, "day" to 1)))
		assertEquals(0L, StoryCalendar.position(GREGORIAN, date("year" to 1970)))
	}

	@Test
	fun `gregorian positions across a leap year`() {
		// 2000-01-01 is day 10957; January and a leap February take 60 days to reach March 1.
		assertEquals(10957L * minutesPerDay, StoryCalendar.position(GREGORIAN, date("year" to 2000)))
		assertEquals(
			11017L * minutesPerDay,
			StoryCalendar.position(GREGORIAN, date("year" to 2000, "month" to 3, "day" to 1)),
		)
		assertEquals(
			11016L * minutesPerDay + 13 * 60 + 45,
			StoryCalendar.position(
				GREGORIAN,
				date("year" to 2000, "month" to 2, "day" to 29, "hour" to 13, "minute" to 45),
			),
		)
	}

	@Test
	fun `gregorian before the unix epoch is negative`() {
		assertEquals(-minutesPerDay, StoryCalendar.position(GREGORIAN, date("year" to 1969, "month" to 12, "day" to 31)))
	}

	@Test
	fun `gregorian year spans follow leap years`() {
		assertEquals(366 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 2024))!!.length())
		assertEquals(365 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 2023))!!.length())
		assertEquals(365 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 1900))!!.length())
		assertEquals(366 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 2000))!!.length())
	}

	@Test
	fun `gregorian month spans`() {
		assertEquals(29 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 2024, "month" to 2))!!.length())
		assertEquals(28 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 2023, "month" to 2))!!.length())
		val december = StoryCalendar.span(GREGORIAN, date("year" to 2023, "month" to 12))!!
		assertEquals(31 * minutesPerDay, december.length())
		assertEquals(StoryCalendar.position(GREGORIAN, date("year" to 2024)), december.last + 1)
	}

	@Test
	fun `gregorian day hour and minute spans`() {
		val day = date("year" to 2024, "month" to 2, "day" to 29)
		val start = StoryCalendar.position(GREGORIAN, day)!!
		assertEquals(start until start + minutesPerDay, StoryCalendar.span(GREGORIAN, day))
		assertEquals(
			start + 60 until start + 120,
			StoryCalendar.span(GREGORIAN, date("year" to 2024, "month" to 2, "day" to 29, "hour" to 1)),
		)
		assertEquals(
			start + 61 until start + 62,
			StoryCalendar.span(GREGORIAN, date("year" to 2024, "month" to 2, "day" to 29, "hour" to 1, "minute" to 1)),
		)
	}

	@Test
	fun `gregorian year zero is 1 BCE and a leap year`() {
		// Proleptic: 0000-01-01 is 719528 days before 1970-01-01, and year 0 has 366 days.
		assertEquals(-719528L * minutesPerDay, StoryCalendar.position(GREGORIAN, date("year" to 0)))
		assertEquals(366 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to 0))!!.length())
		assertEquals(StoryCalendar.position(GREGORIAN, date("year" to 1)), StoryCalendar.span(GREGORIAN, date("year" to 0))!!.last + 1)
		assertNull(StoryCalendar.validate(GREGORIAN, date("year" to 0, "month" to 2, "day" to 29)))
	}

	@Test
	fun `gregorian BCE years run backwards from year zero`() {
		// Year -1 is 2 BCE, a common year; year -4 is 5 BCE, a leap year.
		assertEquals(365 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to -1))!!.length())
		assertEquals(366 * minutesPerDay, StoryCalendar.span(GREGORIAN, date("year" to -4))!!.length())
		assertEquals(StoryCalendar.position(GREGORIAN, date("year" to 0)), StoryCalendar.span(GREGORIAN, date("year" to -1))!!.last + 1)
		assertEquals(
			StoryDateProblem.OutOfRange("day", 29),
			StoryCalendar.validate(GREGORIAN, date("year" to -1, "month" to 2, "day" to 29)),
		)
		assertNull(StoryCalendar.validate(GREGORIAN, date("year" to -4, "month" to 2, "day" to 29)))
	}

	@Test
	fun `gregorian validates months`() {
		assertEquals(StoryDateProblem.OutOfRange("month", 0), StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 0)))
		assertEquals(StoryDateProblem.OutOfRange("month", 13), StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 13)))
		assertEquals(
			StoryDateProblem.OutOfRange("month", 13),
			StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 13, "day" to 40)),
		)
	}

	@Test
	fun `gregorian validates days per month`() {
		assertNull(StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 2, "day" to 29)))
		assertEquals(
			StoryDateProblem.OutOfRange("day", 29),
			StoryCalendar.validate(GREGORIAN, date("year" to 2023, "month" to 2, "day" to 29)),
		)
		assertEquals(
			StoryDateProblem.OutOfRange("day", 29),
			StoryCalendar.validate(GREGORIAN, date("year" to 1900, "month" to 2, "day" to 29)),
		)
		assertEquals(
			StoryDateProblem.OutOfRange("day", 31),
			StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 4, "day" to 31)),
		)
		assertNull(StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 12, "day" to 31)))
		assertEquals(
			StoryDateProblem.OutOfRange("day", 0),
			StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 1, "day" to 0)),
		)
	}

	@Test
	fun `gregorian validates hours and minutes`() {
		assertEquals(
			StoryDateProblem.OutOfRange("hour", 24),
			StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 1, "day" to 1, "hour" to 24)),
		)
		assertEquals(
			StoryDateProblem.OutOfRange("minute", -1),
			StoryCalendar.validate(GREGORIAN, date("year" to 2024, "month" to 1, "day" to 1, "hour" to 0, "minute" to -1)),
		)
	}

	@Test
	fun `gregorian rejects a gap in the chain`() {
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(GREGORIAN, date("year" to 2024, "day" to 3)))
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(GREGORIAN, date("month" to 3)))
	}

	@Test
	fun `gregorian does not support coarsestUnit`() {
		assertNull(StoryCalendar.validate(CalendarDef(preset = GREGORIAN)))
		for (unit in listOf("year", "day", "fortnight")) {
			val calendar = CalendarDef(preset = GREGORIAN, coarsestUnit = unit)
			assertEquals(StoryDateProblem.CoarsestUnitNotSupported, StoryCalendar.validate(calendar))
			assertEquals(
				StoryDateProblem.CoarsestUnitNotSupported,
				StoryCalendar.validate(calendar, date("year" to 2024, "month" to 1, "day" to 31)),
			)
			assertNull(StoryCalendar.position(calendar, date("year" to 2024)))
		}
	}

	@Test
	fun `gregorian dates always start at the year`() {
		val calendar = CalendarDef(preset = GREGORIAN, epochName = "Landing")
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(calendar, date("day" to 31, "hour" to 3)))
		assertEquals(StoryDateProblem.NotAPrefix, StoryCalendar.validate(calendar, date("hour" to 3)))
		assertNull(StoryCalendar.validate(calendar, date("year" to 2024, "month" to 1, "day" to 31, "hour" to 3)))
	}
}
