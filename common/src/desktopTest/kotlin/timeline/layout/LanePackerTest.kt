package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LanePacker
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.PackItem
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanePackerTest {
	private fun span(start: Long, end: Long) = PackItem(start.toFloat(), end.toFloat(), end - start, start)

	@Test
	fun `no two spans in a column overlap`() {
		val random = Random(11)
		repeat(50) {
			val items = (0 until 40).map {
				val start = random.nextLong(0, 1000)
				span(start, start + random.nextLong(1, 200))
			}

			val packing = LanePacker.pack(items)

			for (column in 0 until packing.columnCount) {
				val inColumn = items.indices.filter { packing.columns[it] == column }.sortedBy { items[it].y0 }
				inColumn.zipWithNext().forEach { (a, b) ->
					assertTrue(items[a].y1 <= items[b].y0, "column $column: ${items[a]} overlaps ${items[b]}")
				}
			}
			assertTrue(packing.columns.all { it in 0 until packing.columnCount })
		}
	}

	@Test
	fun `the longest set takes the band even when a shorter span starts first`() {
		val early = span(0, 2)
		val long = span(1, 10)

		val packing = LanePacker.pack(listOf(early, long))

		assertEquals(0, packing.columns[1])
		assertEquals(1, packing.columns[0])
		assertEquals(2, packing.columnCount)
	}

	@Test
	fun `a chain of short spans beats one long span when its total is greater`() {
		val items = listOf(span(0, 4), span(1, 8), span(4, 9))

		val packing = LanePacker.pack(items)

		assertEquals(listOf(0, 1, 0), packing.columns.toList())
	}

	@Test
	fun `equal totals go to the set that starts earlier`() {
		val items = listOf(span(2, 7), span(0, 5))

		val packing = LanePacker.pack(items)

		assertEquals(listOf(1, 0), packing.columns.toList())
	}

	@Test
	fun `abutting spans share a column`() {
		val items = listOf(span(0, 100), span(10, 20), span(20, 30), span(30, 40))

		val packing = LanePacker.pack(items)

		assertEquals(listOf(0, 1, 1, 1), packing.columns.toList())
	}

	@Test
	fun `the staircase sample needs three columns`() {
		val layout = TimelineLayoutEngine.layout(TimelineLayoutSamples.overlappingStack(), MonospaceEstimateMeasurer())

		assertEquals(listOf(2), layout.bands.map { it.eventId })
		assertEquals(mapOf(1 to 1, 3 to 1, 4 to 2, 5 to 1), layout.bars.associate { it.eventId to it.column })
	}

	@Test
	fun `point marks sit in the band column`() {
		val layout = TimelineLayoutEngine.layout(TimelineLayoutSamples.hollowmere(), MonospaceEstimateMeasurer())
		val wardIndex = layout.lanes.single { it.name == TimelineLayoutSamples.HOLLOWMERE_WARD }.index
		val band = layout.bands.first { it.laneIndex == wardIndex }.rect

		val point = layout.points.single { it.eventId == 18 }

		assertTrue(point.x > band.x && point.x < band.right, "point at ${point.x} outside band ${band.x}..${band.right}")
	}
}
