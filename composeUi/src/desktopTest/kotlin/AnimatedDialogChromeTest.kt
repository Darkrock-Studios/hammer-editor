import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import com.darkrockstudios.apps.hammer.common.compose.LocalWindowTitleBarHeight
import org.junit.Rule
import org.junit.Test

/** A dialog layer spans the whole window, so its content keeps clear of the window's own title bar. */
class AnimatedDialogChromeTest {

	@get:Rule
	val compose = createComposeRule()

	private fun mount(titleBarHeight: androidx.compose.ui.unit.Dp) {
		compose.setContent {
			CompositionLocalProvider(LocalWindowTitleBarHeight provides titleBarHeight) {
				AnimatedFullScreenDialog(onDismissed = {}) {
					Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
				}
			}
		}
		compose.waitForIdle()
	}

	@Test
	fun `a full-screen dialog starts below the window's title bar`() {
		mount(titleBarHeight = 40.dp)

		compose.onNodeWithTag(CONTENT_TAG).assertTopPositionInRootIsEqualTo(40.dp)
	}

	@Test
	fun `without a title bar inside the window the dialog starts at the top`() {
		mount(titleBarHeight = 0.dp)

		compose.onNodeWithTag(CONTENT_TAG).assertTopPositionInRootIsEqualTo(0.dp)
	}

	private companion object {
		const val CONTENT_TAG = "dialog-content"
	}
}
