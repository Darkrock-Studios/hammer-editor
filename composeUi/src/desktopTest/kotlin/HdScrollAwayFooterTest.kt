import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdScrollAwayFooter
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayFooterState
import org.junit.Rule
import org.junit.Test

private const val LIST_TAG = "list"
private const val ACTION_TAG = "footer-action"
private val PaneHeight = 300.dp
private val RowHeight = 30.dp
private val ActionHeight = 44.dp

class HdScrollAwayFooterTest {

	@get:Rule
	val compose = createComposeRule()

	private fun showFooter(rowCount: Int) {
		compose.setContent {
			val footerState = rememberHdScrollAwayFooterState()
			val listState = rememberLazyListState()
			Box(modifier = Modifier.size(400.dp, PaneHeight).clipToBounds()) {
				LazyColumn(
					modifier = Modifier
						.fillMaxSize()
						.testTag(LIST_TAG)
						.nestedScroll(footerState.nestedScrollConnection),
					state = listState,
					contentPadding = PaddingValues(bottom = footerState.height),
				) {
					items(rowCount) { index -> TestRow(index) }
				}

				HdScrollAwayFooter(
					state = footerState,
					visible = !listState.canScrollForward || !footerState.isHiddenByScroll,
				) {
					Box(modifier = Modifier.weight(1f).height(ActionHeight).testTag(ACTION_TAG))
				}
			}
		}
	}

	@Test
	fun `Footer hides on a downward scroll`() {
		showFooter(rowCount = 40)

		compose.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }

		compose.onNodeWithTag(ACTION_TAG).assertDoesNotExist()
	}

	@Test
	fun `Footer is pinned once the list bottoms out`() {
		showFooter(rowCount = 40)

		compose.onNodeWithTag(LIST_TAG).performScrollToIndex(39)

		compose.onNodeWithTag(ACTION_TAG).assertIsDisplayed()
	}

	@Test
	fun `Footer returns on an upward scroll`() {
		showFooter(rowCount = 40)

		compose.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }
		compose.onNodeWithTag(LIST_TAG).performTouchInput { swipeDown() }

		compose.onNodeWithTag(ACTION_TAG).assertIsDisplayed()
	}
}

@Composable
private fun TestRow(index: Int) {
	Box(modifier = Modifier.fillMaxWidth().height(RowHeight)) {
		HdMonoLabel(text = "ROW $index")
	}
}
