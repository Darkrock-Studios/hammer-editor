package repositories.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.displayOrder
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.freezeOrder
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.spaceEvenly
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimelineOrderingTest {

	private val story = CalendarDef(CalendarPreset.STORY)
	private val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

	private fun date(vararg rungs: Pair<String, Int>) =
		StoryDate(rungs.associateTo(LinkedHashMap()) { (unit, value) -> unit to value.toLong() })

	private fun event(id: Int, order: Int, start: StoryDate? = null) = TimeLineEvent(id = id, order = order, start = start)

	private fun events(count: Int) = (0 until count).map { event(id = it + 10, order = it) }

	@Test
	fun `manual order follows the order field`() {
		val events = listOf(event(1, 2), event(2, 0), event(3, 1))

		val ordered = displayOrder(events, TimelineMode.MANUAL, null)

		assertEquals(listOf(2, 3, 1), ordered.map { it.id })
	}

	@Test
	fun `dated order follows the start with undated last and ties by order`() {
		val events = listOf(
			event(1, order = 0, start = date("year" to 1, "day" to 5)),
			event(2, order = 1),
			event(3, order = 2, start = date("year" to 1, "day" to 2)),
			event(4, order = 3, start = date("year" to 1, "day" to 5)),
			// A month is not a STORY unit, so this counts as undated.
			event(5, order = 4, start = date("month" to 3)),
			event(6, order = 5, start = date("year" to -2)),
		)

		val ordered = displayOrder(events, TimelineMode.DATED, story)

		assertEquals(listOf(6, 3, 1, 4, 2, 5), ordered.map { it.id })
	}

	@Test
	fun `spaceEvenly steps story days and carries into the year`() {
		val spacing = EvenSpacing(unit = "day", origin = date("year" to 0, "day" to 363, "hour" to 7))

		val dates = spaceEvenly(events(3), story, spacing)

		assertEquals(
			mapOf(
				10 to date("year" to 0, "day" to 363),
				11 to date("year" to 0, "day" to 364),
				12 to date("year" to 1, "day" to 0),
			),
			dates,
		)
	}

	@Test
	fun `spaceEvenly in a story told in hours carries into days`() {
		val calendar = CalendarDef(CalendarPreset.STORY, coarsestUnit = "hour")
		val spacing = EvenSpacing(unit = "hour", step = 3, origin = date("day" to 0, "hour" to 20))

		val dates = spaceEvenly(events(3), calendar, spacing)

		assertEquals(
			mapOf(
				10 to date("day" to 0, "hour" to 20),
				11 to date("day" to 0, "hour" to 23),
				12 to date("day" to 1, "hour" to 2),
			),
			dates,
		)
	}

	@Test
	fun `spaceEvenly from a bare hour origin keeps counting hours`() {
		val calendar = CalendarDef(CalendarPreset.STORY, coarsestUnit = "hour")
		val spacing = EvenSpacing(unit = "hour", step = 20, origin = date("hour" to 0))

		val dates = spaceEvenly(events(3), calendar, spacing)

		assertEquals(listOf(date("hour" to 0), date("hour" to 20), date("hour" to 40)), dates.values.toList())
	}

	@Test
	fun `spaceEvenly steps gregorian months and carries into the year`() {
		val spacing = EvenSpacing(unit = "month", origin = date("year" to 2020, "month" to 11, "day" to 15))

		val dates = spaceEvenly(events(3), gregorian, spacing)

		assertEquals(
			mapOf(
				10 to date("year" to 2020, "month" to 11),
				11 to date("year" to 2020, "month" to 12),
				12 to date("year" to 2021, "month" to 1),
			),
			dates,
		)
	}

	@Test
	fun `spaceEvenly steps gregorian days across a leap day and the year`() {
		val leap = spaceEvenly(
			events(3),
			gregorian,
			EvenSpacing(unit = "day", origin = date("year" to 2020, "month" to 2, "day" to 28)),
		)
		assertEquals(
			listOf(
				date("year" to 2020, "month" to 2, "day" to 28),
				date("year" to 2020, "month" to 2, "day" to 29),
				date("year" to 2020, "month" to 3, "day" to 1),
			),
			leap.values.toList(),
		)

		val bce = spaceEvenly(
			events(2),
			gregorian,
			EvenSpacing(unit = "day", origin = date("year" to 0, "month" to 12, "day" to 31)),
		)
		assertEquals(
			listOf(date("year" to 0, "month" to 12, "day" to 31), date("year" to 1, "month" to 1, "day" to 1)),
			bce.values.toList(),
		)
	}

	@Test
	fun `spaceEvenly steps gregorian years`() {
		val dates = spaceEvenly(events(3), gregorian, EvenSpacing(unit = "year", step = 10, origin = date("year" to 1)))

		assertEquals(listOf(date("year" to 1), date("year" to 11), date("year" to 21)), dates.values.toList())
	}

	@Test
	fun `freezeOrder numbers events by their position`() {
		val ordered = listOf(event(7, order = 3), event(2, order = 0), event(9, order = 1))

		assertEquals(mapOf(7 to 0, 2 to 1, 9 to 2), freezeOrder(ordered))
	}
}
