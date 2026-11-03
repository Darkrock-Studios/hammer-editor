package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaneWidthTest {
	private val measurer = MonospaceEstimateMeasurer()
	private val metrics = LayoutMetrics()

	private fun event(id: Int, start: Long, end: Long? = null, lane: String, milestone: Boolean = false) =
		LayoutEvent(id, LayoutKind.EVENT, "Event $id", null, lane, milestone, id, start, end)

	/** A lane of abutting spans beside one with an overlapping span (a bar) and a labelled point. */
	private fun bandAndBars(width: Float, phone: Boolean = false, frame: TimelineFrame = TimelineFrame.Everything) = TimelineLayoutInput(
		events = listOf(
			event(1, 0, 100, BAND),
			event(2, 100, 200, BAND),
			event(3, 200, 300, BAND),
			event(4, 0, 150, BARS),
			event(5, 50, 250, BARS),
			event(6, 120, lane = BARS),
		),
		laneOrder = listOf(BAND, BARS),
		view = TimelineView.TO_SCALE,
		frame = frame,
		widthDp = width,
		phone = phone,
		ticks = TimelineLayoutSamples.dayClockTicks,
	)

	private fun layout(input: TimelineLayoutInput) = TimelineLayoutEngine.layout(input, measurer)

	private fun TimelineLayout.lane(name: String) = lanes.single { it.name == name }

	private fun TimelineLayout.bandWidths(): List<Float> = bands.map { it.rect.width }.distinct()

	@Test
	fun `a band-only lane is narrower than a lane with bars and leader labels`() {
		val layout = layout(bandAndBars(840f))

		val band = layout.lane(BAND)
		val bars = layout.lane(BARS)
		assertTrue(band.widthDp < bars.widthDp, "band lane ${band.widthDp}, bars lane ${bars.widthDp}")
		assertEquals(2 * 8f + metrics.bandMax, band.widthDp, 1e-3f)
		assertEquals(listOf(metrics.bandMax), layout.bandWidths())
	}

	@Test
	fun `lane widths do not change with the frame`() {
		for (sample in listOf(TimelineLayoutSamples.hollowmere(), TimelineLayoutSamples.leviathan())) {
			val bandId = sample.events.first { it.end != null && it.lane != null }.id
			val everything = layout(sample)
			val framed = layout(sample.copy(frame = TimelineFrame.Band(bandId)))

			assertEquals(everything.lanes, framed.lanes)
			assertEquals(everything.ledgerWidthDp, framed.ledgerWidthDp, 1e-3f)
		}
	}

	@Test
	fun `lanes and ledger never take more than the width`() {
		val samples = listOf(
			TimelineLayoutSamples.hollowmere(),
			TimelineLayoutSamples.leviathan(),
			TimelineLayoutSamples.insurgencyEverything(),
			TimelineLayoutSamples.insurgencyStory(),
			TimelineLayoutSamples.overlappingStack(),
			TimelineLayoutSamples.stress(),
			bandAndBars(840f),
		)
		for (sample in samples) {
			for (width in listOf(480f, 640f, 840f, 1200f, 2400f)) {
				val layout = layout(sample.copy(widthDp = width))

				val lanes = layout.lanes.fold(0f) { sum, lane -> sum + lane.widthDp }
				assertTrue(
					lanes + layout.ledgerWidthDp <= width - metrics.axisWidth - metrics.padRight + 1e-2f,
					"width $width: lanes $lanes, ledger ${layout.ledgerWidthDp}",
				)
				assertEquals(layout.laneRegion.width, lanes, 1e-2f)
				layout.lanes.zipWithNext().forEach { (a, b) -> assertEquals(a.xDp + a.widthDp, b.xDp, 1e-3f) }
			}
		}
	}

	@Test
	fun `tight lanes give up label room before their bands`() {
		val bandNeed = 2 * 8f + metrics.bandMax
		val barsFixed = 2 * 8f + metrics.barOffset + metrics.barStep
		val ledger = metrics.ledgerMin
		val chrome = metrics.axisWidth + metrics.padRight + ledger

		val halfLabels = layout(bandAndBars(chrome + bandNeed + barsFixed + metrics.bandMax + metrics.laneLabelRoom / 2f))
		assertEquals(listOf(metrics.bandMax), halfLabels.bandWidths())
		assertEquals(barsFixed + metrics.bandMax + metrics.laneLabelRoom / 2f, halfLabels.lane(BARS).widthDp, 1e-2f)

		val band = 100f
		val noLabels = layout(bandAndBars(chrome + 2 * 8f + band + barsFixed + band))
		noLabels.bands.forEach { assertEquals(band, it.rect.width, 1e-2f) }
		assertEquals(2 * 8f + band, noLabels.lane(BAND).widthDp, 1e-2f)
		assertEquals(barsFixed + band, noLabels.lane(BARS).widthDp, 1e-2f)
	}

	@Test
	fun `the minimum width keeps every band at its minimum`() {
		val input = bandAndBars(0f)
		val layout = layout(input.copy(widthDp = TimelineLayoutEngine.minimumWidthDp(input, measurer)))

		layout.bands.forEach { assertEquals(metrics.bandMin, it.rect.width, 1e-3f) }
		assertTrue(layout.lanes.all { it.widthDp >= metrics.laneMinWidth - 1e-3f })
	}

	@Test
	fun `phone lanes stay rails`() {
		val layout = layout(bandAndBars(360f, phone = true))

		layout.lanes.forEach { assertEquals(metrics.railWidth, it.widthDp) }
	}

	private companion object {
		const val BAND = "Band"
		const val BARS = "Bars"
	}
}
