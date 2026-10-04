import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.LocalEditorTextStyle
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

private const val VIEW_TAG = "markdown-view"

class MarkdownViewRestyleTest {

	@get:Rule
	val compose = createComposeRule()

	private fun viewHeight() = compose.onNodeWithTag(VIEW_TAG).fetchSemanticsNode().size.height

	@Test
	fun `a new text size resizes text that is already rendered`() {
		var fontSize by mutableStateOf(16f)
		compose.setContent {
			CompositionLocalProvider(LocalEditorTextStyle provides TextStyle(fontSize = fontSize.sp)) {
				Box(modifier = Modifier.width(400.dp)) {
					MarkdownView(
						markdown = "One line of prose.\n\nAnother line of prose.",
						modifier = Modifier.testTag(VIEW_TAG),
					)
				}
			}
		}
		compose.waitForIdle()
		val before = viewHeight()

		fontSize = 32f
		compose.waitForIdle()

		assertTrue(viewHeight() > before, "Text must grow with the font size: $before -> ${viewHeight()}")
	}
}
