import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MARKDOWN_FORMAT_BAR_TABLE_TAG
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownFormatBar
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownFormatBarMenuEscapeTest {

	@Test
	fun `Esc closes the table menu before the Esc ladder steps`() = runComposeUiTest {
		val registry = ActionRegistry()
		setContent {
			KoinApplicationPreview {
				ProvideActions(registry) {
					val state = rememberTextEditorState()
					val markdown = remember(state) { state.withMarkdown(HammerMarkdownConfiguration) }
					MarkdownFormatBar(markdownState = markdown, extended = true)
				}
			}
		}
		waitForIdle()
		onNodeWithTag(MARKDOWN_FORMAT_BAR_TABLE_TAG, useUnmergedTree = true).performClick()
		waitForIdle()

		val overlayWhileOpen = registry.hasOverlay()
		registry.dismissTopOverlay()
		waitForIdle()

		assertTrue(overlayWhileOpen)
		assertFalse(registry.hasOverlay())
	}
}
