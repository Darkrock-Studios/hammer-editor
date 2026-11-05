package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerMark
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
	fun `tight lanes shrink their bands before their label room`() {
		val bandFixed = 2 * 8f
		val barsFixed = 2 * 8f + metrics.barOffset + metrics.barStep
		val chrome = metrics.axisWidth + metrics.padRight + metrics.ledgerMin

		val band = 100f
		val narrowBands = layout(bandAndBars(chrome + bandFixed + band + barsFixed + band + metrics.laneLabelRoom))
		narrowBands.bands.forEach { assertEquals(band, it.rect.width, 1e-2f) }
		assertEquals(barsFixed + band + metrics.laneLabelRoom, narrowBands.lane(BARS).widthDp, 1e-2f)

		val halfLabels = layout(bandAndBars(chrome + bandFixed + barsFixed + 2 * metrics.bandMin + metrics.laneLabelRoom / 2f))
		halfLabels.bands.forEach { assertEquals(metrics.bandMin, it.rect.width, 1e-2f) }
		assertEquals(barsFixed + metrics.bandMin + metrics.laneLabelRoom / 2f, halfLabels.lane(BARS).widthDp, 1e-2f)
	}

	@Test
	fun `label room shrinks only once the bands are at their minimum`() {
		val barsFixed = 2 * 8f + metrics.barOffset + metrics.barStep
		val input = bandAndBars(0f)
		val from = TimelineLayoutEngine.minimumWidthDp(input, measurer).toInt()

		for (width in from..1000 step 5) {
			val layout = layout(input.copy(widthDp = width.toFloat()))

			val band = layout.bands.first().rect.width
			val labelRoom = layout.lane(BARS).widthDp - barsFixed - band
			assertTrue(band >= metrics.bandMin - 1e-3f, "width $width: band $band")
			if (labelRoom < metrics.laneLabelRoom - 1e-2f) assertEquals(metrics.bandMin, band, 1e-2f, "width $width")
		}
	}

	@Test
	fun `hollowmere at 840 keeps its bands at least at their minimum`() {
		val layout = layout(TimelineLayoutSamples.hollowmere().copy(widthDp = 840f))

		assertTrue(layout.bands.isNotEmpty())
		layout.bands.forEach { assertTrue(it.rect.width >= metrics.bandMin - 1e-3f, "band ${it.eventId}: ${it.rect.width}") }
	}

	@Test
	fun `hollowmere at 840 has no empty leader label`() {
		val layout = layout(TimelineLayoutSamples.hollowmere().copy(widthDp = 840f))

		val leaders = layout.bands.mapNotNull { (it.label as? BandLabel.Leader)?.label } +
			layout.bars.mapNotNull { it.label } +
			layout.points.mapNotNull { it.label }
		leaders.forEach { assertTrue(it.lines.any { line -> line.isNotBlank() }, "empty leader label for ${it.fullText}") }
		val billiards = assertNotNull(layout.points.single { it.eventId == PASSES_BILLIARDS }.label)
		assertEquals(listOf("Passes billiards"), billiards.lines)
	}

	@Test
	fun `a squeezed lane moves its leader labels into the ledger`() {
		val layout = layout(TimelineLayoutSamples.hollowmere().copy(widthDp = 640f))

		val leaders = layout.bands.mapNotNull { (it.label as? BandLabel.Leader)?.label } +
			layout.bars.mapNotNull { it.label } +
			layout.points.mapNotNull { it.label }
		leaders.forEach { assertTrue(it.lines.any { line -> line.isNotBlank() }, "empty leader label for ${it.fullText}") }

		val ward = layout.lane(TimelineLayoutSamples.HOLLOWMERE_WARD)
		val billiards = layout.ledgerRows.single { it.eventId == PASSES_BILLIARDS }
		assertEquals(LedgerStyle.LaneCallout, billiards.style)
		assertEquals(LedgerMark.LaneSwatch, billiards.mark)
		assertEquals(ward.index, billiards.laneIndex)
		assertEquals(null, layout.points.single { it.eventId == PASSES_BILLIARDS }.label)
		val source = assertNotNull(billiards.leader).first()
		assertTrue(source.x >= ward.xDp && source.x <= ward.xDp + ward.widthDp, "callout led from ${source.x}, outside ${ward.name}")
	}

	@Test
	fun `a wide lane keeps its leader labels`() {
		val layout = layout(TimelineLayoutSamples.hollowmere().copy(widthDp = 1400f))

		val label = assertNotNull(layout.points.single { it.eventId == PASSES_BILLIARDS }.label)
		assertEquals("Passes billiards", label.fullText)
		assertTrue(layout.ledgerRows.none { it.style == LedgerStyle.LaneCallout })
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
		const val PASSES_BILLIARDS = 18
	}
}
