import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.toggleBlockquote
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.isBlockquote
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatBarQuoteTest {

	@Test
	fun `a line quoted from the format bar stays quoted after saving`() = runTest {
		val editor = TextEditorState(scope = this, measurer = mockk(relaxed = true)).withMarkdown(HammerMarkdownConfiguration)
		editor.importMarkdown("A line to quote.")
		editor.editorState.cursor.updatePosition(CharLineOffset(0, 2))
		toggleBlockquote(editor.editorState)

		val saved = editor.exportAsMarkdown()
		assertEquals("> A line to quote.", saved)

		val reopened = TextEditorState(scope = this, measurer = mockk(relaxed = true)).withMarkdown(HammerMarkdownConfiguration)
		reopened.importMarkdown(saved)
		assertTrue(reopened.editorState.isBlockquote(0))
	}
}
