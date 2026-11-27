import androidx.compose.ui.text.style.TextAlign
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.alignParagraphs
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.paragraphFormat
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatBarAlignmentTest {

	private fun TestScope.editor() =
		TextEditorState(scope = this, measurer = mockk(relaxed = true)).withMarkdown(HammerMarkdownConfiguration)

	@Test
	fun `a line centred from the format bar stays centred after saving`() = runTest {
		val editor = editor()
		editor.importMarkdown("Before.\nThe end.\nAfter.")
		editor.editorState.cursor.updatePosition(CharLineOffset(1, 2))
		alignParagraphs(editor.editorState, TextAlign.Center)

		val saved = editor.exportAsMarkdown()
		assertEquals("Before.\n<div align=\"center\">\n\nThe end.\n\n</div>\n\nAfter.", saved)

		val reopened = editor()
		reopened.importMarkdown(saved)
		assertEquals(listOf("Before.", "The end.", "After."), reopened.editorState.textLines.map { it.text })
		assertEquals(TextAlign.Center, reopened.editorState.paragraphFormat(1)?.textAlign)
	}

	@Test
	fun `aligning left clears the line's alignment`() = runTest {
		val editor = editor()
		editor.importMarkdown("<div align=\"right\">\n\nRight.\n\n</div>")
		editor.editorState.cursor.updatePosition(CharLineOffset(0, 0))
		alignParagraphs(editor.editorState, null)

		assertNull(editor.editorState.paragraphFormat(0))
		assertEquals("Right.", editor.exportAsMarkdown())
	}

	@Test
	fun `a selection aligns its paragraphs and leaves table cells and list items alone`() = runTest {
		val editor = editor()
		editor.importMarkdown("Prose.\n\n| a | b |\n| --- | --- |\n| c | d |\n\n- item")
		val state = editor.editorState
		val last = state.textLines.lastIndex
		state.selector.updateSelection(CharLineOffset(0, 0), CharLineOffset(last, 1))
		alignParagraphs(state, TextAlign.Center)

		assertEquals(TextAlign.Center, state.paragraphFormat(0)?.textAlign)
		for (line in 1..last) assertNull(state.paragraphFormat(line), "line $line")
	}
}
