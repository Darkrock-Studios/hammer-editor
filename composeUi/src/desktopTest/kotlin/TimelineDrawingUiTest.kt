import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_HIT_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawing
import com.darkrockstudios.apps.hammer.common.timeline.drawing.testTag
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitRegion
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

private const val HOST_TAG = "Timeline Host"

class TimelineDrawingUiTest {
	@get:Rule
	val compose = createComposeRule()

	private val input = TimelineLayoutSamples.hollowmere()
	private val layout: TimelineLayout = TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer())

	private fun show(
		layout: TimelineLayout = this.layout,
		selectedId: Int? = null,
		onTap: (HitTarget) -> Unit = {},
		onPeek: (Int?) -> Unit = {},
		highlighted: Set<Int> = emptySet(),
	) {
		compose.setContent {
			MaterialTheme {
				Box(modifier = Modifier.requiredWidth(layout.widthDp.dp).testTag(HOST_TAG)) {
					TimelineDrawing(
						layout = layout,
						laneColor = { Color.Gray },
						selectedId = selectedId,
						onTap = onTap,
						onPeek = onPeek,
						highlighted = highlighted,
						focusedId = highlighted.firstOrNull(),
						titles = input.events.associate { it.id to it.title },
					)
				}
			}
		}
	}

	private fun assertPlacedOn(region: HitRegion) {
		val host = compose.onNodeWithTag(HOST_TAG).getBoundsInRoot()
		val bounds = compose.onNodeWithTag(region.target.testTag()).getBoundsInRoot()
		val tolerance = 1f
		assertEquals(region.rect.x, (bounds.left - host.left).value, absoluteTolerance = tolerance)
		assertEquals(region.rect.y, (bounds.top - host.top).value, absoluteTolerance = tolerance)
		assertEquals(region.rect.width, (bounds.right - bounds.left).value, absoluteTolerance = tolerance)
		assertEquals(region.rect.height, (bounds.bottom - bounds.top).value, absoluteTolerance = tolerance)
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
	fun `A long press peeks at its event until the press is released`() {
		val tapped = mutableListOf<HitTarget>()
		val peeks = mutableListOf<Int?>()
		show(onTap = { tapped += it }, onPeek = { peeks += it })
		val band = layout.bands.minBy { it.rect.y }
		val node = compose.onNodeWithTag(HitTarget.Band(band.eventId).testTag())

		node.performTouchInput { down(center) }
		compose.mainClock.advanceTimeBy(1_000)
		compose.waitForIdle()

		assertEquals(listOf<Int?>(band.eventId), peeks)

		node.performTouchInput { up() }
		compose.waitForIdle()

		assertEquals(listOf(band.eventId, null), peeks)
		assertEquals(emptyList(), tapped, "A long press does not tap")
	}

	@Test
	fun `A tap does not peek`() {
		val peeks = mutableListOf<Int?>()
		show(onPeek = { peeks += it })
		val row = layout.ledgerRows.minBy { it.anchorYDp }

		compose.onNodeWithTag(HitTarget.Ledger(row.eventId).testTag()).performClick()
		compose.waitForIdle()

		assertEquals(emptyList(), peeks)
	}

	@Test
	fun `Tapping a lane header reports its lane`() {
		val tapped = mutableListOf<HitTarget>()
		show(onTap = { tapped += it })

		compose.onNodeWithTag(HitTarget.LaneHeader(TimelineLayoutSamples.HOLLOWMERE_HAYES).testTag()).performClick()

		assertEquals(listOf<HitTarget>(HitTarget.LaneHeader(TimelineLayoutSamples.HOLLOWMERE_HAYES)), tapped)
	}

	@Test
	fun `A ledger row's hit box covers its region`() {
		show()
		val row = layout.ledgerRows.maxBy { it.anchorYDp }

		assertPlacedOn(layout.hitRegions.first { it.target == HitTarget.Ledger(row.eventId) })
	}

	@Test
	fun `A band's hit box covers its region`() {
		show()
		val band = layout.bands.maxBy { it.rect.y }

		assertPlacedOn(layout.hitRegions.first { it.target == HitTarget.Band(band.eventId) })
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
	fun `A search veils everything but its matches`() {
		val (match, other) = layout.bands.filter { it.rect.height > 12f }.sortedBy { it.rect.y }.take(2)
		fun centre(band: com.darkrockstudios.apps.hammer.common.timeline.layout.BandShape): androidx.compose.ui.graphics.Color {
			val density = compose.density.density
			val pixels = compose.onNodeWithTag(HOST_TAG).captureToImage().toPixelMap()
			return pixels[((band.rect.x + band.rect.width / 2f) * density).toInt(), ((band.rect.y + band.rect.height / 2f) * density).toInt()]
		}
		var highlighted by mutableStateOf(emptySet<Int>())
		compose.setContent {
			MaterialTheme {
				Box(modifier = Modifier.requiredWidth(layout.widthDp.dp).testTag(HOST_TAG)) {
					TimelineDrawing(
						layout = layout,
						laneColor = { Color(0xFF3366CC) },
						selectedId = null,
						onTap = {},
						highlighted = highlighted,
						focusedId = highlighted.firstOrNull(),
					)
				}
			}
		}
		val matchBefore = centre(match)
		val otherBefore = centre(other)

		highlighted = setOf(match.eventId)
		compose.waitForIdle()

		assertEquals(matchBefore, centre(match), "the match keeps its colour")
		assertTrue(centre(other) != otherBefore, "everything else is veiled")
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

	@Test
	fun `Hovering a ledger row projects its moment across the lanes until the pointer leaves`() {
		val row = layout.ledgerRows.filter { it.anchorY1Dp != null }.minBy { it.anchorYDp }
		val y1 = assertNotNull(row.anchorY1Dp)
		show()
		val rowNode = compose.onNodeWithTag(HitTarget.Ledger(row.eventId).testTag())

		// The host, not the root: a hovered row's tooltip opens a second root.
		fun strip(): List<Int> {
			val density = compose.density.density
			val pixels = compose.onNodeWithTag(HOST_TAG).captureToImage().toPixelMap()
			val y = ((row.anchorYDp + y1) / 2f * density).toInt()
			val x0 = ((layout.laneRegion.x + 2f) * density).toInt()
			val x1 = ((layout.laneRegion.right - 2f) * density).toInt()
			return (x0 until x1).map { pixels[it, y].hashCode() }
		}

		val before = strip()
		rowNode.performMouseInput { enter(center) }
		compose.waitForIdle()
		val hovered = strip()
		rowNode.performMouseInput { exit() }
		compose.waitForIdle()
		val after = strip()

		assertTrue(before != hovered, "the hovered row's span is not tinted across the lanes")
		assertEquals(before, after, "the tint stays after the pointer leaves")
	}
}
