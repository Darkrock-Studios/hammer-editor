import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

	@Test
	fun `A mouse wheel scrolls the chips sideways`() {
		showBar()
		assertFalse(shown("tag number 4 · 4"))

		compose.onNodeWithText("tag number 1 · 1").performMouseInput {
			moveTo(center)
			repeat(4) { scroll(1f, ScrollWheel.Vertical) }
		}

		assertTrue(shown("tag number 4 · 4"))
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
}
