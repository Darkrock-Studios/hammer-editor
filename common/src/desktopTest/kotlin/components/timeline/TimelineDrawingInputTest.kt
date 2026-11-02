package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.timelineTickScheme
import com.darkrockstudios.apps.hammer.common.components.timeline.toLayoutInput
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimelineDrawingInputTest {

	private val story = CalendarDef(epochName = "Landing")
	private val minute = 1L
	private val hour = 60 * minute
	private val day = 24 * hour
	private val year = 365 * day

	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	private fun row(
		id: Int,
		start: StoryDate?,
		end: StoryDate? = null,
		kind: TimelineKind = TimelineKind.EVENT,
		lane: String? = null,
		milestone: Boolean = false,
		subtitle: String? = null,
	) = TimelineRow(
		event = TimeLineEvent(id = id, order = id, kind = kind, title = "Event $id", lane = lane, milestone = milestone, start = start, end = end),
		title = "Event $id",
		subtitle = subtitle,
		whenText = "",
		laneIndex = null,
		start = start,
		end = end,
		calendar = story.takeIf { start != null },
	)

	private fun input(rows: List<TimelineRow>, laneFilter: Set<String> = emptySet(), calendar: CalendarDef = story) =
		toLayoutInput(
			rows = rows,
			lanes = listOf(LaneCount("Alice", 1), LaneCount("Queen", 1)),
			laneFilter = laneFilter,
			calendar = calendar,
			view = TimelineView.TO_SCALE,
			widthDp = 840f,
			phone = false,
		)

	@Test
	fun `events carry their positions, kinds and placement data`() {
		val rows = listOf(
			row(1, date("year" to 2, "day" to 3, "hour" to 4, "minute" to 5), lane = "Alice", milestone = true),
			row(
				2,
				date("year" to -1, "day" to 0, "hour" to 0, "minute" to 0),
				end = date("year" to 0, "day" to 1, "hour" to 0, "minute" to 0),
				kind = TimelineKind.SCENE,
				subtitle = "Chapter 1",
			),
		)

		val events = input(rows).events

		assertEquals(listOf(1, 2), events.map { it.id })
		assertEquals(2 * year + 3 * day + 4 * hour + 5, events[0].start)
		assertEquals(null, events[0].end)
		assertEquals(1L, events[0].precisionExtent, "A minute-precise point has no extent")
		assertEquals(true, events[0].milestone)
		assertEquals("Alice", events[0].lane)
		assertEquals(LayoutKind.SCENE, events[1].kind)
		assertEquals(-year, events[1].start)
		assertEquals(day, events[1].end)
		assertEquals("Chapter 1", events[1].subtitle)
		assertEquals(listOf("Alice", "Queen"), input(rows).laneOrder)
	}

	@Test
	fun `a day-precision start spans its day`() {
		val events = input(listOf(row(1, date("year" to 1, "day" to 10)))).events

		assertEquals(year + 10 * day, events.single().start)
		assertEquals(day, events.single().precisionExtent)
	}

	@Test
	fun `a span keeps no precision extent`() {
		val events = input(listOf(row(1, date("year" to 1), end = date("year" to 3)))).events

		assertEquals(1L, events.single().precisionExtent)
		assertEquals(3 * year, events.single().end)
	}

	@Test
	fun `undated and unresolvable starts are left out`() {
		val rows = listOf(
			row(1, date("year" to 1)),
			row(2, null),
			row(3, date("month" to 4)),
		)

		assertEquals(listOf(1), input(rows).events.map { it.id })
	}

	@Test
	fun `the lane filter drops other lanes but keeps scenes and unlaned events`() {
		val start = date("year" to 1)
		val rows = listOf(
			row(1, start, lane = "Alice"),
			row(2, start, lane = "Queen"),
			row(3, start, lane = "Queen", kind = TimelineKind.SCENE),
			row(4, start),
		)

		assertEquals(listOf(1, 3, 4), input(rows, laneFilter = setOf("Alice")).events.map { it.id })
		assertEquals(listOf(1, 2, 3, 4), input(rows).events.map { it.id })
	}

	@Test
	fun `ticks run in the calendar's units and label in its words`() {
		val scheme = timelineTickScheme(story)
		assertEquals(listOf("year", "day", "hour", "minute"), scheme.units.map { it.name })
		assertEquals(listOf(year, day, hour, minute), scheme.units.map { it.sizeInBase })

		val dayUnit = scheme.units[1]
		val hourUnit = scheme.units[2]
		assertEquals("Year 12 · Day 40", scheme.format(12 * year + 40 * day + 5 * hour, dayUnit, false))
		assertEquals("12 · 40", scheme.format(12 * year + 40 * day, dayUnit, true))
		assertEquals("Day 40 · 03:00", scheme.format(12 * year + 40 * day + 3 * hour + 15, hourUnit, false))
		assertEquals("03:00", scheme.format(12 * year + 40 * day + 3 * hour, hourUnit, true))
		assertEquals("Year 3 before Landing", scheme.format(-3 * year, scheme.units[0], false))
	}

	@Test
	fun `a story told in hours ticks in hours and minutes`() {
		val scheme = timelineTickScheme(CalendarDef(coarsestUnit = "hour"))
		assertEquals(listOf("hour", "minute"), scheme.units.map { it.name })
		assertEquals("Hour 30", scheme.format(30 * hour + 12, scheme.units[0], false))
	}

	private val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

	private fun gregorianAt(vararg rungs: Pair<String, Long>): Long = StoryCalendar.position(gregorian, date(*rungs))!!

	@Test
	fun `gregorian year ticks land on the first of January`() {
		val scheme = timelineTickScheme(gregorian)
		val yearUnit = scheme.units.first()
		assertEquals("year", yearUnit.name)
		val range = gregorianAt("year" to 1800)..gregorianAt("year" to 1810)

		val positions = scheme.positionsIn(range, yearUnit, 1)

		assertEquals((1800L..1810L).map { gregorianAt("year" to it) }, positions)
		assertEquals((1800..1810).map { "$it" }, positions.map { scheme.format(it, yearUnit, false) })
	}

	@Test
	fun `gregorian year ticks step from a multiple of the step`() {
		val scheme = timelineTickScheme(gregorian)
		val yearUnit = scheme.units.first()
		val range = gregorianAt("year" to 1803, "month" to 6)..gregorianAt("year" to 1821)

		val positions = scheme.positionsIn(range, yearUnit, 5)

		assertEquals(listOf(1805L, 1810L, 1815L, 1820L).map { gregorianAt("year" to it) }, positions)
	}

	@Test
	fun `gregorian month ticks land on the first of each month`() {
		val scheme = timelineTickScheme(gregorian)
		val monthUnit = scheme.units.single { it.name == "month" }
		val range = gregorianAt("year" to 1802, "month" to 1, "day" to 15)..gregorianAt("year" to 1802, "month" to 6, "day" to 1)

		val positions = scheme.positionsIn(range, monthUnit, 1)

		assertEquals((2L..6L).map { gregorianAt("year" to 1802, "month" to it) }, positions)
		assertEquals(
			listOf("February 1802", "March 1802", "April 1802", "May 1802", "June 1802"),
			positions.map { scheme.format(it, monthUnit, false) },
		)
	}

	@Test
	fun `gregorian day ticks label the day`() {
		val scheme = timelineTickScheme(gregorian)
		val dayUnit = scheme.units.single { it.name == "day" }

		assertEquals("12 May 1802", scheme.format(gregorianAt("year" to 1802, "month" to 5, "day" to 12, "hour" to 6), dayUnit, false))
	}
}
