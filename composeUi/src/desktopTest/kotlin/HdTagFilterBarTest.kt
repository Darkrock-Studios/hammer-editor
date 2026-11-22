import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HdTagFilterBarTest {
	@get:Rule
	val compose = createComposeRule()

	private val tags = (1..12).map { TagCount("tag number $it", it) }
	private val toggled = mutableListOf<String>()

	private fun showBar() {
		compose.setContent {
			HdTagFilterBar(
				tags = tags,
				allLabel = "ALL · 12",
				activeTags = emptySet(),
				onToggle = { toggled += it },
				onClear = {},
				modifier = Modifier.requiredWidth(360.dp),
			)
		}
	}

	private fun shown(text: String): Boolean =
		runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess

	private fun firstShownTag(): Int = tags.indices.map { it + 1 }.first { shown("tag number $it · $it") }

	@Test
	fun `A mouse wheel scrolls the chips sideways`() {
		showBar()
		assertEquals(1, firstShownTag())
		val before = compose.onNodeWithText("tag number 1 · 1").getBoundsInRoot().left

		compose.onNodeWithText("tag number 1 · 1").performMouseInput {
			moveTo(center)
			repeat(4) { scroll(1f, ScrollWheel.Vertical) }
		}

		// Font widths decide how far four ticks go, so check only that the row moved left.
		val first = firstShownTag()
		assertTrue(first > 1 || compose.onNodeWithText("tag number 1 · 1").getBoundsInRoot().left < before)
	}

	@Test
	fun `A mouse drag pans the chips`() {
		showBar()
		assertFalse(shown("tag number 4 · 4"))

		compose.onNodeWithText("tag number 1 · 1").performMouseInput {
			moveTo(center)
			press()
			repeat(20) { moveBy(Offset(-15.dp.toPx(), 0f)) }
			release()
		}

		assertTrue(shown("tag number 4 · 4"))
		assertEquals(emptyList(), toggled)
	}

	@Test
	fun `A tag named like the bar's own leading item still shows`() {
		compose.setContent {
			HdTagFilterBar(
				tags = listOf(TagCount("scrolling-leading", 2), TagCount("scrolling-leading-rule", 1)),
				allLabel = "ALL · 2",
				activeTags = emptySet(),
				onToggle = {},
				onClear = {},
				modifier = Modifier.requiredWidth(360.dp),
				scrollingLeading = { androidx.compose.material3.Text("Sort") },
			)
		}

		assertTrue(shown("scrolling-leading · 2"))
		assertTrue(shown("scrolling-leading-rule · 1"))
	}
}
