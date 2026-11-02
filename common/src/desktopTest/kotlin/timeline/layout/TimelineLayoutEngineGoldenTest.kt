package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitRegion
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Structural snapshots of every sample at desktop (840dp) and phone (360dp) widths. */
class TimelineLayoutEngineGoldenTest {
	private val measurer = MonospaceEstimateMeasurer()

	private data class Shape(
		val bands: Int,
		val bars: Int,
		val points: Int,
		val ledgerRows: Int,
		val folds: Int,
		val milestoneRules: Int,
		val height: Float,
	)

	private class Sample(
		val name: String,
		val selectId: Int,
		val desktop: Shape,
		val phone: Shape,
		val build: (phone: Boolean, selectedId: Int?) -> TimelineLayoutInput,
	)

	private val samples = listOf(
		Sample(
			"minimal", 4,
			desktop = Shape(0, 0, 0, 6, 2, 1, 317.2f),
			phone = Shape(0, 0, 0, 6, 2, 1, 317.2f),
		) { phone, id -> TimelineLayoutSamples.minimal(phone, id) },
		Sample(
			"hollowmere", 2,
			desktop = Shape(17, 0, 2, 11, 0, 2, 1088f),
			phone = Shape(17, 0, 2, 29, 0, 2, 1088f),
		) { phone, id -> TimelineLayoutSamples.hollowmere(phone, id) },
		Sample(
			"leviathan", 8,
			desktop = Shape(20, 0, 7, 9, 2, 5, 1375f),
			phone = Shape(20, 0, 7, 32, 2, 5, 1375f),
		) { phone, id -> TimelineLayoutSamples.leviathan(phone, id) },
		Sample(
			"insurgencyEverything", 19,
			desktop = Shape(10, 0, 8, 4, 2, 2, 1288f),
			phone = Shape(10, 0, 8, 20, 2, 2, 1288f),
		) { phone, id -> TimelineLayoutSamples.insurgencyEverything(phone, id) },
		Sample(
			"insurgencyStory", 21,
			desktop = Shape(1, 0, 7, 14, 0, 0, 941f),
			phone = Shape(1, 0, 7, 22, 0, 0, 935f),
		) { phone, id -> TimelineLayoutSamples.insurgencyStory(phone, id) },
		Sample(
			"overlappingStack", 2,
			desktop = Shape(1, 4, 0, 0, 0, 0, 474.7f),
			phone = Shape(1, 4, 0, 5, 0, 0, 474.7f),
		) { phone, id -> TimelineLayoutSamples.overlappingStack(phone, id) },
		Sample(
			"stress", 1,
			desktop = Shape(99, 18, 269, 119, 47, 6, 33404f),
			phone = Shape(99, 18, 269, 500, 47, 6, 33398f),
		) { phone, id -> TimelineLayoutSamples.stress(phone = phone, selectedId = id) },
	)

	private fun forEachLayout(block: (sample: Sample, phone: Boolean, layout: TimelineLayout) -> Unit) {
		for (sample in samples) {
			for (phone in listOf(false, true)) {
				block(sample, phone, TimelineLayoutEngine.layout(sample.build(phone, null), measurer))
			}
		}
	}

	@Test
	fun `each sample keeps its shape`() = forEachLayout { sample, phone, layout ->
		val expected = if (phone) sample.phone else sample.desktop
		val actual = Shape(
			layout.bands.size,
			layout.bars.size,
			layout.points.size,
			layout.ledgerRows.size,
			layout.folds.size,
			layout.milestoneRules.size,
			expected.height,
		)

		assertEquals(expected, actual, "${sample.name} phone=$phone")
		assertEquals(expected.height, layout.heightDp, 1f, "${sample.name} phone=$phone height")
	}

	@Test
	fun `point marks are drawn after every span in their lane`() = forEachLayout { sample, phone, layout ->
		val laneOf = HashMap<Int, Int>()
		layout.bands.forEach { laneOf[it.eventId] = it.laneIndex }
		layout.bars.forEach { laneOf[it.eventId] = it.laneIndex }
		layout.points.forEach { laneOf[it.eventId] = it.laneIndex }

		for (lane in layout.lanes) {
			val order = layout.hitRegions.withIndex().filter { (_, region) ->
				val id = when (val t = region.target) {
					is HitTarget.Band -> t.eventId
					is HitTarget.Point -> t.eventId
					else -> null
				}
				id != null && laneOf[id] == lane.index
			}
			val lastSpan = order.lastOrNull { it.value.target is HitTarget.Band }?.index ?: continue
			val firstPoint = order.firstOrNull { it.value.target is HitTarget.Point && isMark(layout, it.value) }?.index ?: continue
			assertTrue(lastSpan < firstPoint, "${sample.name} phone=$phone lane ${lane.name}: a point is drawn before a span")
		}
	}

	/** The point's mark region, as opposed to the hit region of its leader label. */
	private fun isMark(layout: TimelineLayout, region: HitRegion): Boolean {
		val id = (region.target as HitTarget.Point).eventId
		val point = layout.points.single { it.eventId == id }
		return region.rect.contains(point.x, point.y)
	}

	@Test
	fun `phone lanes are rails without band labels or headers`() = forEachLayout { sample, phone, layout ->
		if (!phone) return@forEachLayout

		layout.lanes.forEach {
			assertEquals(22f, it.widthDp, "${sample.name} lane ${it.name}")
			assertEquals(null, it.header)
		}
		assertTrue(layout.bands.all { it.label == BandLabel.None }, "${sample.name} has a band label on phone")
		assertTrue(layout.bars.all { it.label == null } && layout.points.all { it.label == null }, "${sample.name} has a lane label on phone")
	}

	@Test
	fun `the desktop ledger stays within its width bounds`() = forEachLayout { sample, phone, layout ->
		if (phone || layout.lanes.isEmpty()) return@forEachLayout

		assertTrue(layout.ledgerWidthDp in 96f..118f, "${sample.name} ledger ${layout.ledgerWidthDp}")
	}

	@Test
	fun `spans stay inside their lane`() = forEachLayout { sample, phone, layout ->
		val shapes = layout.bands.map { it.laneIndex to it.rect } + layout.bars.map { it.laneIndex to it.rect }
		for ((laneIndex, rect) in shapes) {
			val lane = layout.lanes[laneIndex]
			assertTrue(rect.x >= lane.xDp && rect.right <= lane.xDp + lane.widthDp + 1e-3f, "${sample.name} phone=$phone: $rect outside lane ${lane.name}")
		}
	}

	@Test
	fun `ledger rows never overlap`() = forEachLayout { sample, phone, layout ->
		layout.ledgerRows.map { it.hitRect }.sortedBy { it.y }.zipWithNext().forEach { (a, b) ->
			assertTrue(b.y >= a.bottom - 1e-3f, "${sample.name} phone=$phone: ledger rows overlap at ${b.y}")
		}
	}

	@Test
	fun `a selection is projected`() {
		for (sample in samples) {
			for (phone in listOf(false, true)) {
				val layout = TimelineLayoutEngine.layout(sample.build(phone, sample.selectId), measurer)

				assertEquals(sample.selectId, assertNotNull(layout.projection, "${sample.name} phone=$phone").eventId)
			}
		}
	}

	@Test
	fun `layout is deterministic`() {
		val first = TimelineLayoutEngine.layout(TimelineLayoutSamples.stress(), measurer)
		val second = TimelineLayoutEngine.layout(TimelineLayoutSamples.stress(), measurer)

		assertEquals(first, second)
	}
}
