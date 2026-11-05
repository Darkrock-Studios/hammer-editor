package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TickUnit
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.deriveTickUnit
import com.darkrockstudios.apps.hammer.common.timeline.layout.resolveFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples.DAY
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples.HOUR
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples.YEAR
import com.darkrockstudios.apps.hammer.common.timeline.layout.tickPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickUnitsTest {
	private val dayClock = TimelineLayoutSamples.dayClockTicks.units
	private val years = TimelineLayoutSamples.landingYearTicks.units

	private fun event(
		id: Int,
		start: Long,
		end: Long? = null,
		kind: LayoutKind = LayoutKind.EVENT,
		extent: Long = 1,
		milestone: Boolean = false,
	) = LayoutEvent(id, kind, "Event $id", null, null, milestone, id, start, end, extent)

	@Test
	fun `a nine day frame ticks in days`() {
		val step = deriveTickUnit(0L..9 * DAY, dayClock, 8)!!

		assertEquals("day", step.unit.name)
		assertEquals(1, step.step)
		assertEquals(10, tickPositions(0L..9 * DAY, step.unit, step.step).size)
	}

	@Test
	fun `an eight hour frame ticks in hours`() {
		val step = deriveTickUnit(19 * HOUR..27 * HOUR, dayClock, 8)!!

		assertEquals("hour", step.unit.name)
		assertEquals(1, step.step)
	}

	@Test
	fun `a long frame steps up the 1 2 5 ladder`() {
		val step = deriveTickUnit(1952 * YEAR, years, 8)!!

		assertEquals("year", step.unit.name)
		assertEquals(250, step.step)
	}

	@Test
	fun `a frame shorter than every unit falls back to the finest`() {
		val step = deriveTickUnit(10L, dayClock, 8)!!

		assertEquals("minute", step.unit.name)
		assertEquals(1, step.step)
	}

	@Test
	fun `a unit's own steps come before the 1 2 5 ladder`() {
		val clock = listOf(
			TickUnit("hour", HOUR, listOf(1, 2, 3, 6, 12)),
			TickUnit("minute", 1, listOf(1, 2, 5, 10, 15, 30)),
		)

		assertEquals(15, deriveTickUnit(150L, clock, 8)!!.step)
		assertEquals(30, deriveTickUnit(200L, clock, 8)!!.step)
		// Past the unit's own steps the ladder carries on above the last of them.
		assertEquals(50, deriveTickUnit(500L, listOf(clock[1]), 8)!!.step)
	}

	@Test
	fun `no units means no ticks`() {
		assertEquals(null, deriveTickUnit(100L, emptyList(), 8))
	}

	@Test
	fun `tick positions are multiples of the step, also before zero`() {
		val positions = tickPositions(-25L..21L, TickUnit("ten", 10), 1)

		assertEquals(listOf(-20L, -10L, 0L, 10L, 20L), positions)
	}

	@Test
	fun `insurgency everything ticks about every thirty years`() {
		val layout = TimelineLayoutEngine.layout(TimelineLayoutSamples.insurgencyEverything(), MonospaceEstimateMeasurer())

		val gaps = layout.axis.ticks.map { it.position }.zipWithNext { a, b -> b - a }
		val step = gaps.min()
		assertTrue(step in 20 * YEAR..50 * YEAR, "step of ${step / YEAR} years")
		assertTrue(layout.axis.ticks.any { it.position == -1800 * YEAR }, "the fold edge at Launch is labelled")
	}

	@Test
	fun `every sample shows between 4 and 12 ticks`() {
		val samples: List<(Boolean) -> TimelineLayoutInput> = listOf(
			{ TimelineLayoutSamples.minimal(it) },
			{ TimelineLayoutSamples.hollowmere(it) },
			{ TimelineLayoutSamples.leviathan(it) },
			{ TimelineLayoutSamples.insurgencyEverything(it) },
			{ TimelineLayoutSamples.insurgencyStory(it) },
			{ TimelineLayoutSamples.overlappingStack(it) },
		)
		for (sample in samples) {
			for (phone in listOf(false, true)) {
				val input = sample(phone)
				val count = TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer()).axis.ticks.size

				assertTrue(count in 4..12, "${input.events.first().title}: $count ticks")
			}
		}
	}

	@Test
	fun `everything spans every event`() {
		val events = listOf(event(1, 10), event(2, 50, 90), event(3, -5, extent = 20))

		assertEquals(-5L..90L, resolveFrame(events, TimelineFrame.Everything))
	}

	@Test
	fun `story spans the scenes`() {
		val events = listOf(event(1, 0, 1000), event(2, 100, kind = LayoutKind.SCENE), event(3, 300, 400, LayoutKind.SCENE))

		assertEquals(100L..400L, resolveFrame(events, TimelineFrame.Story))
	}

	@Test
	fun `story takes in a milestone just past the last scene`() {
		val scenes = listOf(event(2, 100, kind = LayoutKind.SCENE), event(3, 300, 400, LayoutKind.SCENE))
		val near = event(4, 415, milestone = true)
		val far = event(5, 550, milestone = true)
		val notMilestone = event(6, 410)

		assertEquals(100L..415L, resolveFrame(scenes + near + far + notMilestone, TimelineFrame.Story))
	}

	@Test
	fun `story leaves out a milestone half its length past the scenes`() {
		val events = listOf(
			event(2, 100, kind = LayoutKind.SCENE),
			event(3, 300, 400, LayoutKind.SCENE),
			event(4, -50, milestone = true),
			event(5, 550, milestone = true),
		)

		assertEquals(100L..400L, resolveFrame(events, TimelineFrame.Story))
	}

	@Test
	fun `story widens only as far as its reach for a long milestone`() {
		val events = listOf(
			event(2, 100, kind = LayoutKind.SCENE),
			event(3, 300, 400, LayoutKind.SCENE),
			event(4, 380, 2000, milestone = true),
		)

		assertEquals(100L..430L, resolveFrame(events, TimelineFrame.Story))
	}

	@Test
	fun `story without scenes shows everything`() {
		val events = listOf(event(1, 0, 1000))

		assertEquals(0L..1000L, resolveFrame(events, TimelineFrame.Story))
	}

	@Test
	fun `a band frame pads the band by five percent, at least one unit`() {
		val events = listOf(event(1, 1000, 3000), event(2, 50, 60), event(3, 500))

		assertEquals(900L..3100L, resolveFrame(events, TimelineFrame.Band(1)))
		assertEquals(49L..61L, resolveFrame(events, TimelineFrame.Band(2)))
		assertEquals(499L..501L, resolveFrame(events, TimelineFrame.Band(3)))
		assertEquals(50L..3000L, resolveFrame(events, TimelineFrame.Band(99)), "an unknown band shows everything")
	}
}
