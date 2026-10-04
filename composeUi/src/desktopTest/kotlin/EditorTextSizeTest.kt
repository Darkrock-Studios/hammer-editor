import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.withParagraphIndent
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DOC =
	"# Title\n\n**bold** and *it* and [l](http://x.y) and `code`\n\n## Two\n\n> quote\n\n- a\n- b\n\n#### Four\n\nplain"

@OptIn(ExperimentalTestApi::class)
class EditorTextSizeTest {

	@Test
	fun `markdown round trips through the relative styles`() = runTest {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		state.richTextStyles = HammerRichTextStyles
		val markdown = state.withMarkdown(HammerMarkdownConfiguration)

		markdown.importMarkdown(DOC)

		assertEquals(DOC, markdown.exportAsMarkdown())
	}

	@Test
	fun `the body size scales every line without touching the document`() {
		var bodySize by mutableStateOf(16f)
		var state: TextEditorState? = null
		runComposeUiTest {
			setContent {
				val editorState = rememberTextEditorState()
				state = editorState
				TextEditor(
					state = editorState,
					style = rememberTextEditorStyle(
						textStyle = TextStyle(fontSize = bodySize.sp).withParagraphIndent(),
					),
				)
			}
			waitForIdle()
			val editorState = state!!
			editorState.richTextStyles = HammerRichTextStyles
			editorState.withMarkdown(HammerMarkdownConfiguration).importMarkdown(DOC)
			waitForIdle()

			fun lineHeights() = editorState.lineOffsets
				.filter { it.virtualLineIndex == 0 }
				.associate { it.line to it.textLayoutResult.size.height }
			fun indentOf(line: Int) = editorState.lineOffsets
				.first { it.line == line && it.virtualLineIndex == 0 }
				.textLayoutResult.getHorizontalPosition(0, usePrimaryDirection = true)

			val small = lineHeights()
			val smallIndent = indentOf(small.keys.max())
			val linesBefore = editorState.textLines.toList()
			bodySize = 32f
			waitForIdle()
			val large = lineHeights()
			val largeIndent = indentOf(small.keys.max())

			assertEquals(linesBefore, editorState.textLines.toList())
			assertEquals(24f, smallIndent)
			assertEquals(48f, largeIndent)
			val lastLine = small.keys.max()
			assertTrue(small.getValue(0) > small.getValue(lastLine) * 1.6f)
			small.keys.forEach { line ->
				val ratio = large.getValue(line).toFloat() / small.getValue(line)
				assertTrue(ratio in 1.8f..2.2f)
			}
		}
	}
}
