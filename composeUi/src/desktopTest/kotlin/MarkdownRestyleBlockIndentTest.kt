import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStylesDark
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateRichTextStyles
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A theme change restyles every line in place, which must not cost block lines the
 * ParagraphStyle their gutter markers are positioned against.
 */
class MarkdownRestyleBlockIndentTest {

	private fun TestScope.newEditor(): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		state.richTextStyles = HammerRichTextStyles
		return state.withMarkdown(HammerMarkdownConfiguration)
	}

	@Test
	fun `list indents survive a theme change`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("A paragraph.\n\n1. one\n2. two\n\n- bullet\n\n> quoted")
		val before = editor.editorState.textLines.map { it.paragraphStyles }

		editor.editorState.updateRichTextStyles(HammerRichTextStylesDark)

		assertEquals(before, editor.editorState.textLines.map { it.paragraphStyles })
	}
}
