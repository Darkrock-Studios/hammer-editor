import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_SCROLL_AWAY_STRIP_TAG
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdScrollAwayStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayStripState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val CONTENT_TAG = "content"
private val PaneHeight = 400.dp
private val StripHeight = 100.dp

class HdScrollAwayStripTest {

	@get:Rule
	val compose = createComposeRule()

	private var contentHeight by mutableStateOf(0.dp)

	private fun show(height: Dp) {
		contentHeight = height
		compose.setContent {
			val strip = rememberHdScrollAwayStripState()
			val scroll: ScrollState = rememberScrollState()
			LaunchedEffect(strip, scroll) { snapshotFlow { scroll.value }.collect(strip::followScrollPosition) }
			HdScrollAwayStrip(
				state = strip,
				modifier = Modifier.size(400.dp, PaneHeight),
				strip = { Spacer(modifier = Modifier.fillMaxWidth().height(StripHeight)) },
			) { topPadding ->
				Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).testTag(CONTENT_TAG)) {
					Spacer(modifier = Modifier.height(topPadding))
					Box(modifier = Modifier.fillMaxWidth().height(contentHeight))
				}
			}
		}
	}

	private fun stripTop() = compose.onNodeWithTag(HD_SCROLL_AWAY_STRIP_TAG).getUnclippedBoundsInRoot().top

	@Test
	fun `Content that fits only once the strip hides still brings it back when scrolled up`() {
		// Taller than the pane under a shown strip, shorter than the whole pane.
		show(350.dp)

		compose.onNodeWithTag(CONTENT_TAG).performMouseInput { scroll(10f) }
		compose.waitForIdle()
		compose.onNodeWithTag(HD_SCROLL_AWAY_STRIP_TAG).assertIsNotDisplayed()

		compose.onNodeWithTag(CONTENT_TAG).performMouseInput { scroll(-10f) }
		compose.waitForIdle()

		assertEquals(0f, stripTop().value, absoluteTolerance = 1f)
		compose.onNodeWithTag(HD_SCROLL_AWAY_STRIP_TAG).assertIsDisplayed()
	}

	@Test
	fun `Starting to scroll long content down hides the strip and scrolling up shows it`() {
		show(2000.dp)

		compose.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()
		compose.onNodeWithTag(HD_SCROLL_AWAY_STRIP_TAG).assertIsNotDisplayed()

		compose.onNodeWithTag(CONTENT_TAG).performMouseInput { scroll(-1f) }
		compose.waitForIdle()

		assertEquals(0f, stripTop().value, absoluteTolerance = 1f)
	}

	@Test
	fun `Content that shrinks back to its top shows the strip again`() {
		show(2000.dp)
		compose.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()

		compose.runOnIdle { contentHeight = 100.dp }
		compose.waitForIdle()

		assertEquals(0f, stripTop().value, absoluteTolerance = 1f)
	}

	@Test
	fun `Content that fits leaves the strip alone`() {
		show(200.dp)

		compose.onNodeWithTag(CONTENT_TAG).performMouseInput { scroll(10f) }
		compose.waitForIdle()

		assertEquals(0f, stripTop().value, absoluteTolerance = 1f)
	}
}
