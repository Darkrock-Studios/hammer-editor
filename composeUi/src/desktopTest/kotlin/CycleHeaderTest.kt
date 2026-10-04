import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.cycleHeader
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.headerLevel
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CycleHeaderTest {

	private fun TestScope.newEditor(): MarkdownExtension =
		TextEditorState(scope = this, measurer = mockk(relaxed = true)).withMarkdown(HammerMarkdownConfiguration)

	@Test
	fun `cycling exports as a markdown heading`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Title\n\nBody")

		cycleHeader(editor.editorState, currentLevel = 0)

		assertEquals(1, editor.editorState.headerLevel(0))
		assertEquals("# Title", editor.exportAsMarkdown().lineSequence().first())
	}

	@Test
	fun `cycling past the last level removes the heading`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("### Title")

		cycleHeader(editor.editorState, currentLevel = 3)

		assertNull(editor.editorState.headerLevel(0))
		assertEquals("Title", editor.exportAsMarkdown().lineSequence().first())
	}

	@Test
	fun `cycling past the last level clears a mixed selection`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("### Title\nBody")
		editor.editorState.selector.updateSelection(CharLineOffset(0, 0), CharLineOffset(1, 4))

		cycleHeader(editor.editorState, currentLevel = 3)

		assertNull(editor.editorState.headerLevel(0))
		assertNull(editor.editorState.headerLevel(1))
	}
}
