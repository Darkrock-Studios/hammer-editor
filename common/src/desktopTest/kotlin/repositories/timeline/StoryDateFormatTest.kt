package repositories.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDate
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StoryDateFormatTest {

	private val story = CalendarDef()
	private val landing = CalendarDef(epochName = "Landing")
	private val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	@Test
	fun `A story date names each filled unit`() {
		assertEquals("Year 12 · Day 40", formatStoryDate(story, date("year" to 12, "day" to 40)))
		assertEquals("Year 12", formatStoryDate(story, date("year" to 12)))
		assertEquals("Year 12 · Day 40 · Hour 6", formatStoryDate(story, date("year" to 12, "day" to 40, "hour" to 6)))
	}

	@Test
	fun `A story hour with its minute reads as a clock time`() {
		assertEquals(
			"Year 1 · Day 2 · 06:05",
			formatStoryDate(story, date("year" to 1, "day" to 2, "hour" to 6, "minute" to 5)),
		)
	}

	@Test
	fun `A leading hour is unbounded, so it keeps its name`() {
		val hours = CalendarDef(coarsestUnit = "hour")
		assertEquals("Hour 30 · Minute 5", formatStoryDate(hours, date("hour" to 30, "minute" to 5)))
	}

	@Test
	fun `A negative coarsest rung falls before the epoch`() {
		assertEquals("Year 3 before Landing", formatStoryDate(landing, date("year" to -3)))
		assertEquals("Year 3 before Landing · Day 10", formatStoryDate(landing, date("year" to -3, "day" to 10)))
	}

	@Test
	fun `Without an epoch a negative year keeps its sign`() {
		assertEquals("Year -3", formatStoryDate(story, date("year" to -3)))
	}

	@Test
	fun `A gregorian date reads day, month name, year and time`() {
		assertEquals(
			"12 May 1802 14:30",
			formatStoryDate(gregorian, date("year" to 1802, "month" to 5, "day" to 12, "hour" to 14, "minute" to 30)),
		)
		assertEquals("12 May 1802", formatStoryDate(gregorian, date("year" to 1802, "month" to 5, "day" to 12)))
		assertEquals("May 1802", formatStoryDate(gregorian, date("year" to 1802, "month" to 5)))
		assertEquals("1802", formatStoryDate(gregorian, date("year" to 1802)))
		assertEquals("1 January 1802 09:00", formatStoryDate(gregorian, date("year" to 1802, "month" to 1, "day" to 1, "hour" to 9)))
	}

	@Test
	fun `Gregorian years before year one are BCE`() {
		assertEquals("1 BCE", formatStoryDate(gregorian, date("year" to 0)))
		assertEquals("44 BCE", formatStoryDate(gregorian, date("year" to -43)))
	}

	@Test
	fun `A date that does not fit the calendar falls back to its compact text`() {
		assertEquals("year=1 day=400", formatStoryDate(story, date("year" to 1, "day" to 400)))
		assertEquals("year=1 day=3", formatStoryDate(gregorian, date("year" to 1, "day" to 3)))
	}

	@Test
	fun `A range joins start and end`() {
		assertEquals("Year 1 → Year 2", formatStoryDateRange(story, date("year" to 1), date("year" to 2)))
		assertEquals("Year 1", formatStoryDateRange(story, date("year" to 1), null))
	}

	@Test
	fun `A range that ends where it starts reads as one moment`() {
		val instant = date("year" to 2, "day" to 3, "hour" to 4, "minute" to 5)
		assertEquals("Year 2 · Day 3 · 04:05", formatStoryDateRange(story, instant, instant))
		assertEquals("Year 2 · Day 3", formatStoryDateRange(story, date("year" to 2, "day" to 3), date("year" to 2, "day" to 3, "hour" to 0, "minute" to 0)))
		assertEquals("Year 2 · Day 3 → Year 2 · Day 3", formatStoryDateRange(story, date("year" to 2, "day" to 3), date("year" to 2, "day" to 3)))
		assertEquals("Year 2 · Day 3 · 04:05 → Year 2 · Day 3 · 04:06", formatStoryDateRange(story, instant, date("year" to 2, "day" to 3, "hour" to 4, "minute" to 6)))
	}

	@Test
	fun `Entry units start at the coarsest unit a story is told in`() {
		assertEquals(listOf("year", "day", "hour", "minute"), entryUnits(story).map { it.name })
		assertEquals(listOf("day", "hour", "minute"), entryUnits(CalendarDef(coarsestUnit = "day")).map { it.name })
		assertEquals(listOf("year", "month", "day", "hour", "minute"), entryUnits(gregorian).map { it.name })
	}
}
