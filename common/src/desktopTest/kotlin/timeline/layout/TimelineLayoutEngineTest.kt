package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerMark
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.PointGlyph
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples.DAY
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples.HOUR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TimelineLayoutEngineTest {
	private val measurer = MonospaceEstimateMeasurer()

	private fun event(
		id: Int,
		start: Long,
		end: Long? = null,
		lane: String? = "A",
		kind: LayoutKind = LayoutKind.EVENT,
		milestone: Boolean = false,
		extent: Long = 1,
		title: String = "Event $id",
	) = LayoutEvent(id, kind, title, null, lane, milestone, id, start, end, extent)

	private fun input(
		events: List<LayoutEvent>,
		frame: TimelineFrame = TimelineFrame.Everything,
		laneOrder: List<String> = emptyList(),
		view: TimelineView = TimelineView.TO_SCALE,
	) = TimelineLayoutInput(events, laneOrder, view, frame, 840f, false, TimelineLayoutSamples.dayClockTicks)

	@Test
	fun `a date with coarse precision lays out as a circa span`() {
		val layout = TimelineLayoutEngine.layout(input(listOf(event(1, 0, extent = 1440), event(2, 2000))), measurer)

		val band = layout.bands.single()
		assertEquals(1, band.eventId)
		assertTrue(band.circa)
		assertEquals(listOf(2), layout.points.map { it.eventId })
	}

	@Test
	fun `events outside the frame are dropped and spans across its edge are clamped`() {
		val events = listOf(
			event(1, 0, 100),
			event(2, 50, 500),
			event(3, 1000),
			event(4, 120, kind = LayoutKind.SCENE, lane = null),
		)

		val layout = TimelineLayoutEngine.layout(input(events, TimelineFrame.Band(1)), measurer)

		assertEquals(listOf(1, 2), layout.bands.map { it.eventId } + layout.bars.map { it.eventId })
		val cut = layout.bars.single { it.eventId == 2 }
		assertEquals(layout.heightDp - 20f, cut.rect.bottom, 1e-3f, "event 2 ends at the bottom of the frame")
		assertTrue(layout.points.isEmpty())
		assertTrue(layout.ledgerRows.isEmpty())
	}

	@Test
	fun `lanes follow the lane order, then the rest alphabetically`() {
		val events = listOf(event(1, 0, lane = "Zed"), event(2, 10, lane = "Bee"), event(3, 20, lane = "Ant"), event(4, 30, lane = " "))

		val layout = TimelineLayoutEngine.layout(input(events, laneOrder = listOf("Zed", "Gone")), measurer)

		assertEquals(listOf("Zed", "Ant", "Bee"), layout.lanes.map { it.name })
		assertEquals(LedgerMark.Dot, layout.ledgerRows.single().mark, "a blank lane counts as unlaned")
	}

	@Test
	fun `a long lane name is truncated in its header`() {
		val events = listOf(event(1, 0, lane = "A very long lane name indeed"), event(2, 0, lane = "B"), event(3, 0, lane = "C"), event(4, 0, lane = "D"), event(5, 0, lane = "E"))

		val lane = TimelineLayoutEngine.layout(input(events), measurer).lanes.first()

		assertTrue(lane.headerTruncated)
		assertTrue(lane.header!!.endsWith("…"))
	}

	@Test
	fun `point glyphs follow kind and milestone`() {
		val events = listOf(event(1, 0), event(2, 10, kind = LayoutKind.ENTRY), event(3, 20, milestone = true))

		val glyphs = TimelineLayoutEngine.layout(input(events), measurer).points.associate { it.eventId to it.glyph }

		assertEquals(mapOf(1 to PointGlyph.Diamond, 2 to PointGlyph.EntryBox, 3 to PointGlyph.MilestoneDiamond), glyphs)
	}

	@Test
	fun `abutting band segments alternate their shade`() {
		val events = (0 until 5).map { event(it + 1, it * 100L, (it + 1) * 100L) }

		val shades = TimelineLayoutEngine.layout(input(events), measurer).bands.map { it.shadeIndex }

		assertEquals(listOf(0, 1, 2, 0, 1), shades)
	}

	@Test
	fun `the sequence view gives every moment its own row`() {
		val sample = TimelineLayoutSamples.leviathan()

		val layout = TimelineLayoutEngine.layout(sample.copy(view = TimelineView.SEQUENCE), measurer)

		assertTrue(layout.folds.isEmpty())
		val ys = layout.axis.ticks.map { it.yDp }
		assertEquals(sample.events.map { it.start }.distinct().size, ys.size)
		ys.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 28f - 1e-3f) }
	}

	@Test
	fun `a laned milestone's long title widens the ledger`() {
		val short = listOf(event(1, 0), event(2, 100, milestone = true, title = "Siege"))
		val long = listOf(event(1, 0), event(2, 100, milestone = true, title = "Canterbury answers the Scopuli"))

		val narrow = TimelineLayoutEngine.layout(input(short), measurer)
		val wide = TimelineLayoutEngine.layout(input(long), measurer)

		val metrics = LayoutMetrics()
		assertEquals(metrics.ledgerMin, narrow.ledgerWidthDp, 1e-3f)
		assertEquals(metrics.ledgerPad + measurer.widthDp("Canterbury answers", LabelStyle.LedgerMilestone), wide.ledgerWidthDp, 1e-3f)
		assertEquals(listOf("Canterbury answers", "the Scopuli"), wide.ledgerRows.single { it.eventId == 2 }.lines)
	}

	@Test
	fun `a laned milestone span keeps its band label and gets a ledger row`() {
		val events = listOf(event(1, 0, 2 * DAY, milestone = true, title = "Siege"), event(2, 3 * DAY))

		val layout = TimelineLayoutEngine.layout(input(events), measurer)

		assertTrue(layout.bands.single().label is BandLabel.Lines)
		val row = layout.ledgerRows.single { it.eventId == 1 }
		assertEquals(LedgerStyle.Milestone, row.style)
		assertEquals(LedgerMark.None, row.mark)
		assertEquals(layout.milestoneRules.single().yDp, row.anchorYDp)
	}

	@Test
	fun `sequence ticks use the coarsest unit that tells the closest moments apart`() {
		val events = listOf(event(1, 0), event(2, 12 * HOUR), event(3, DAY))

		val labels = TimelineLayoutEngine.layout(input(events, view = TimelineView.SEQUENCE), measurer).axis.ticks.map { it.label }

		assertEquals(listOf("Day 1 · 00:00", "Day 1 · 12:00", "Day 2 · 00:00"), labels)
	}

	@Test
	fun `the sequence view never repeats an axis label on consecutive ticks`() {
		val samples = listOf(
			TimelineLayoutSamples.minimal(),
			TimelineLayoutSamples.hollowmere(),
			TimelineLayoutSamples.leviathan(),
			TimelineLayoutSamples.insurgencyEverything(),
			TimelineLayoutSamples.insurgencyStory(),
			TimelineLayoutSamples.overlappingStack(),
			TimelineLayoutSamples.stress(),
		)

		for ((i, sample) in samples.withIndex()) {
			val labels = TimelineLayoutEngine.layout(sample.copy(view = TimelineView.SEQUENCE), measurer).axis.ticks.map { it.label }

			labels.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b, "sample $i repeats $a") }
		}
	}

	@Test
	fun `an empty timeline lays out without content`() {
		val layout = TimelineLayoutEngine.layout(input(emptyList()), measurer)

		assertTrue(layout.lanes.isEmpty() && layout.ledgerRows.isEmpty() && layout.bands.isEmpty())
		assertEquals(LayoutMetrics().let { it.padTop + it.padBottom }, layout.heightDp)
	}
}
