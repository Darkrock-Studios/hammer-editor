package repositories.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateTick
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.storyDateAt
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StoryDateAtTest {

	private val story = CalendarDef()
	private val hours = CalendarDef(coarsestUnit = "hour")
	private val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	private fun assertRoundTrip(calendar: CalendarDef, date: StoryDate, unit: String) {
		val position = StoryCalendar.position(calendar, date)!!
		assertEquals(date, storyDateAt(calendar, position, unit))
	}

	@Test
	fun `story dates round trip at every unit`() {
		assertRoundTrip(story, date("year" to 12), "year")
		assertRoundTrip(story, date("year" to 12, "day" to 40), "day")
		assertRoundTrip(story, date("year" to 12, "day" to 40, "hour" to 6), "hour")
		assertRoundTrip(story, date("year" to 12, "day" to 40, "hour" to 6, "minute" to 59), "minute")
		assertRoundTrip(story, date("year" to -3, "day" to 364, "hour" to 23, "minute" to 1), "minute")
	}

	@Test
	fun `a story position is truncated to the unit`() {
		val position = StoryCalendar.position(story, date("year" to -1, "day" to 200, "hour" to 13, "minute" to 30))!!
		assertEquals(date("year" to -1, "day" to 200), storyDateAt(story, position, "day"))
		assertEquals(date("year" to -1), storyDateAt(story, position, "year"))
	}

	@Test
	fun `a story told in hours starts at the hour`() {
		assertRoundTrip(hours, date("hour" to 30, "minute" to 5), "minute")
		assertRoundTrip(hours, date("hour" to -2), "hour")
		assertEquals(date("hour" to 30), storyDateAt(hours, 30 * 60L + 5, "hour"))
		assertEquals(date("hour" to 30), storyDateAt(hours, 30 * 60L + 5, "day"), "A unit above the coarsest reads as the coarsest")
	}

	@Test
	fun `gregorian dates round trip at every unit`() {
		assertRoundTrip(gregorian, date("year" to 1802), "year")
		assertRoundTrip(gregorian, date("year" to 1802, "month" to 5), "month")
		assertRoundTrip(gregorian, date("year" to 1802, "month" to 5, "day" to 12), "day")
		assertRoundTrip(gregorian, date("year" to 2024, "month" to 2, "day" to 29, "hour" to 23), "hour")
		assertRoundTrip(gregorian, date("year" to -43, "month" to 3, "day" to 15, "hour" to 11, "minute" to 7), "minute")
	}

	@Test
	fun `a gregorian position is truncated to the unit`() {
		val position = StoryCalendar.position(gregorian, date("year" to 1802, "month" to 5, "day" to 12, "hour" to 14, "minute" to 30))!!
		assertEquals(date("year" to 1802, "month" to 5), storyDateAt(gregorian, position, "month"))
		assertEquals(date("year" to 1802), storyDateAt(gregorian, position, "year"))
	}

	@Test
	fun `tick labels keep the two finest parts, or the values alone when short`() {
		val landing = CalendarDef(epochName = "Landing")
		assertEquals("Year 12 · Day 40", formatStoryDateTick(landing, date("year" to 12, "day" to 40), short = false))
		assertEquals("Day 40 · 06:00", formatStoryDateTick(landing, date("year" to 12, "day" to 40, "hour" to 6), short = false))
		assertEquals("06:00", formatStoryDateTick(landing, date("year" to 12, "day" to 40, "hour" to 6), short = true))
		assertEquals("12 · 40", formatStoryDateTick(landing, date("year" to 12, "day" to 40), short = true))
		assertEquals("Year 3 before Landing", formatStoryDateTick(landing, date("year" to -3), short = false))
		assertEquals("May 1802", formatStoryDateTick(gregorian, date("year" to 1802, "month" to 5), short = false))
		assertEquals("12 May", formatStoryDateTick(gregorian, date("year" to 1802, "month" to 5, "day" to 12), short = true))
		assertEquals(
			"12 May · 06:00",
			formatStoryDateTick(gregorian, date("year" to 1802, "month" to 5, "day" to 12, "hour" to 6), short = false),
		)
	}
}
