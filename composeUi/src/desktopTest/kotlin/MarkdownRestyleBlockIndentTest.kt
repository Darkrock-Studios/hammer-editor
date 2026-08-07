import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.markdown.changeFontSize
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.state.TextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Changing the editor font size restyles every line in place, which must not cost
 * block lines the ParagraphStyle their gutter markers are positioned against.
 */
class MarkdownRestyleBlockIndentTest {

	private fun TestScope.newEditor(config: MarkdownConfiguration): MarkdownExtension =
		MarkdownExtension(TextEditorState(scope = this, measurer = mockk(relaxed = true)), config)

	@Test
	fun `list indents survive a font size change`() = runTest {
		val editor = newEditor(MarkdownConfiguration.DEFAULT)
		editor.importMarkdown("A paragraph.\n\n1. one\n2. two\n\n- bullet\n\n> quoted")
		val before = editor.editorState.textLines.map { it.paragraphStyles }

		editor.updateMarkdownConfiguration(MarkdownConfiguration.DEFAULT.changeFontSize(24f))

		assertEquals(before, editor.editorState.textLines.map { it.paragraphStyles })
	}

	@Test
	fun `body text tracks the new font size`() = runTest {
		val editor = newEditor(MarkdownConfiguration.DEFAULT)
		editor.importMarkdown("A paragraph.\n\n1. one")

		editor.updateMarkdownConfiguration(MarkdownConfiguration.DEFAULT.changeFontSize(24f))

		editor.editorState.textLines.filter { it.text.isNotEmpty() }.forEach { line ->
			assertEquals(listOf(24f.sp), line.spanStyles.map { it.item.fontSize })
		}
	}
}
