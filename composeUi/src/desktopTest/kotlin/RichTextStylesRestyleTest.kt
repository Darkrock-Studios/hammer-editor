import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.HammerRichTextStylesDark
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateRichTextStyles
import com.darkrockstudios.texteditor.RichTextStyles
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Switching themes restyles the document in place; the body size never does (see EditorTextSizeTest). */
class RichTextStylesRestyleTest {

	private fun TestScope.newEditor(styles: RichTextStyles): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		state.richTextStyles = styles
		return state.withMarkdown(HammerMarkdownConfiguration)
	}

	@Test
	fun `switching themes leaves one baked style per heading`() = runTest {
		val editor = newEditor(HammerRichTextStyles)
		editor.importMarkdown("# Chapter One\n\nSome body text.")
		val before = editor.editorState.textLines[0].spanStyles.size

		repeat(5) { step ->
			editor.editorState.updateRichTextStyles(
				if (step % 2 == 0) HammerRichTextStylesDark else HammerRichTextStyles
			)
		}

		assertEquals(before, editor.editorState.textLines[0].spanStyles.size)
	}

	@Test
	fun `code spans take the new theme's background`() = runTest {
		val editor = newEditor(HammerRichTextStyles)
		editor.importMarkdown("Some `code` here.")

		editor.editorState.updateRichTextStyles(HammerRichTextStylesDark)

		val backgrounds = editor.editorState.textLines[0].spanStyles.map { it.item.background }
		assertTrue(HammerRichTextStylesDark.codeStyle.background in backgrounds)
		assertTrue(HammerRichTextStyles.codeStyle.background !in backgrounds)
	}
}
