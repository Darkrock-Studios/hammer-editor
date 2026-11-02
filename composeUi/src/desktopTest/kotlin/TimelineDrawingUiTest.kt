import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_HIT_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawing
import com.darkrockstudios.apps.hammer.common.timeline.drawing.testTag
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineDrawingUiTest {
	@get:Rule
	val compose = createComposeRule()

	private val input = TimelineLayoutSamples.hollowmere()
	private val layout: TimelineLayout = TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer())

	private fun show(
		layout: TimelineLayout = this.layout,
		selectedId: Int? = null,
		onTap: (HitTarget) -> Unit = {},
	) {
		compose.setContent {
			MaterialTheme {
				Box(modifier = Modifier.requiredWidth(layout.widthDp.dp)) {
					TimelineDrawing(
						layout = layout,
						laneColor = { Color.Gray },
						selectedId = selectedId,
						onTap = onTap,
						titles = input.events.associate { it.id to it.title },
					)
				}
			}
		}
	}

	private val hitNodes = SemanticsMatcher("is a hit region") { node ->
		node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(TIMELINE_HIT_TAG) == true
	}

	@Test
	fun `Tapping a ledger row reports its event`() {
		val tapped = mutableListOf<HitTarget>()
		show(onTap = { tapped += it })
		val row = layout.ledgerRows.minBy { it.anchorYDp }

		compose.onNodeWithTag(HitTarget.Ledger(row.eventId).testTag()).performClick()

		assertEquals(listOf<HitTarget>(HitTarget.Ledger(row.eventId)), tapped)
	}

	@Test
	fun `Tapping a band reports its event`() {
		val tapped = mutableListOf<HitTarget>()
		show(onTap = { tapped += it })
		val band = layout.bands.minBy { it.rect.y }

		compose.onNodeWithTag(HitTarget.Band(band.eventId).testTag()).performClick()

		assertEquals(listOf<HitTarget>(HitTarget.Band(band.eventId)), tapped)
	}

	@Test
	fun `Tapping a lane header reports its lane`() {
		val tapped = mutableListOf<HitTarget>()
		show(onTap = { tapped += it })

		compose.onNodeWithTag(HitTarget.LaneHeader(TimelineLayoutSamples.HOLLOWMERE_HAYES).testTag()).performClick()

		assertEquals(listOf<HitTarget>(HitTarget.LaneHeader(TimelineLayoutSamples.HOLLOWMERE_HAYES)), tapped)
	}

	@Test
	fun `Every hit region is a described button`() {
		show()

		val nodes = compose.onAllNodes(hitNodes, useUnmergedTree = true).fetchSemanticsNodes()
		assertEquals(layout.hitRegions.size, nodes.size)
		for (node in nodes) {
			val tag = node.config[SemanticsProperties.TestTag]
			val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
			assertTrue(description.isNotBlank(), "$tag has no description")
			assertEquals(
				Role.Button,
				node.config.getOrNull(SemanticsProperties.Role),
				"$tag is not a button",
			)
		}
	}

	@Test
	fun `A selected span is projected across the lanes from its start to its end`() {
		val band = layout.bands
			.filter { band -> input.events.first { it.id == band.eventId }.end != null }
			.maxBy { it.rect.height }
		assertNull(layout.projection, "Nothing is projected without a selection")
		val selected = TimelineLayoutEngine.layout(input.copy(selectedId = band.eventId), MonospaceEstimateMeasurer())

		show(layout = selected, selectedId = band.eventId)

		val projection = assertNotNull(selected.projection)
		assertEquals(band.eventId, projection.eventId)
		assertEquals(band.rect.y, projection.yDp, absoluteTolerance = 1f)
		assertEquals(band.rect.bottom, assertNotNull(projection.y1Dp), absoluteTolerance = 1f)
		assertEquals(selected.laneRegion.width, projection.rect.width)
		compose.onNodeWithTag(HitTarget.Band(band.eventId).testTag()).assertExists()
	}
}
