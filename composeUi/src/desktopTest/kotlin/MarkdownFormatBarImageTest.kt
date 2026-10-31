import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownFormatBar
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MarkdownFormatBarImageTest {

	@get:Rule
	val compose = createComposeRule()

	private fun mount(onInsertImage: (() -> Unit)?) {
		compose.setContent {
			KoinApplicationPreview {
				val state = rememberTextEditorState()
				val markdown = remember(state) { state.withMarkdown(HammerMarkdownConfiguration) }
				MarkdownFormatBar(markdownState = markdown, onInsertImage = onInsertImage)
			}
		}
	}

	@Test
	fun `the insert image button is offered only with an insert action`() {
		mount(onInsertImage = null)

		compose.onNodeWithTag(MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `the insert image button runs the insert action`() {
		var clicks = 0
		mount(onInsertImage = { clicks++ })

		compose.onNodeWithTag(MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG, useUnmergedTree = true).performClick()

		compose.runOnIdle { assertEquals(1, clicks) }
	}
}
