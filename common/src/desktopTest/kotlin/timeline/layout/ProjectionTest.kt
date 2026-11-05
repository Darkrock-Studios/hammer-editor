package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectionTest {
	private val measurer = MonospaceEstimateMeasurer()

	private fun layout(input: TimelineLayoutInput) = TimelineLayoutEngine.layout(input, measurer)

	@Test
	fun `a selected scene projects its moment across the lanes`() {
		val result = layout(TimelineLayoutSamples.leviathan())

		val projection = assertNotNull(result.projectionOf(18))
		val row = result.ledgerRows.single { it.eventId == 18 }
		assertEquals(row.anchorYDp, projection.yDp)
		assertNull(projection.y1Dp)
		assertEquals(0f, projection.rect.height)
		assertEquals(result.laneRegion.x, projection.rect.x)
		assertEquals(result.laneRegion.width, projection.rect.width)
	}

	@Test
	fun `a selected span tints its whole stretch`() {
		val result = layout(TimelineLayoutSamples.hollowmere())

		val projection = assertNotNull(result.projectionOf(2))
		val row = result.ledgerRows.single { it.eventId == 2 }
		assertEquals(row.anchorYDp, projection.yDp)
		assertEquals(row.anchorY1Dp, projection.y1Dp)
		assertEquals(row.anchorY1Dp!! - row.anchorYDp, projection.rect.height)
	}

	@Test
	fun `a selected laned span projects from its band`() {
		val result = layout(TimelineLayoutSamples.hollowmere())

		val band = result.bands.single { it.eventId == 10 }
		assertEquals(band.rect.y, assertNotNull(result.projectionOf(10)).yDp)
	}

	@Test
	fun `no selection, no projection`() {
		assertNull(layout(TimelineLayoutSamples.hollowmere()).projectionOf(null))
	}

	@Test
	fun `a selection outside the frame or unknown is not projected`() {
		assertNull(layout(TimelineLayoutSamples.insurgencyStory()).projectionOf(4), "Gen 5 due lies after the story")
		assertNull(layout(TimelineLayoutSamples.hollowmere()).projectionOf(999))
	}
}
