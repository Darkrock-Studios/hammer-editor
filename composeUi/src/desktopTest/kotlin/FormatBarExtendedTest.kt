import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStylesDark
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.toggleTaskList
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.RichTextStyles
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.isTask
import com.darkrockstudios.texteditor.state.toggleSpanStyle
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The extended format bar's highlight and task list, as notes and other in-app text save them. */
class FormatBarExtendedTest {

	private fun TestScope.newEditor(styles: RichTextStyles = HammerRichTextStyles): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		state.richTextStyles = styles
		return state.withMarkdown(HammerMarkdownConfiguration)
	}

	@Test
	fun `a highlight stays a highlight after saving, in either theme`() = runTest {
		for (styles in listOf(HammerRichTextStyles, HammerRichTextStylesDark)) {
			val editor = newEditor(styles)
			editor.importMarkdown("A word to mark.")
			editor.editorState.selector.updateSelection(CharLineOffset(0, 2), CharLineOffset(0, 6))
			editor.editorState.toggleSpanStyle(styles.highlightStyle)

			val saved = editor.exportAsMarkdown()
			assertEquals("A ==word== to mark.", saved)

			val reopened = newEditor(styles)
			reopened.importMarkdown(saved)
			assertEquals(saved, reopened.exportAsMarkdown())
		}
	}

	@Test
	fun `a task stays a task after saving`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Buy milk")
		editor.editorState.cursor.updatePosition(CharLineOffset(0, 0))
		toggleTaskList(editor.editorState)

		val saved = editor.exportAsMarkdown()
		assertEquals("- [ ] Buy milk", saved)

		val reopened = newEditor()
		reopened.importMarkdown(saved)
		assertTrue(reopened.editorState.isTask(0))
	}
}
