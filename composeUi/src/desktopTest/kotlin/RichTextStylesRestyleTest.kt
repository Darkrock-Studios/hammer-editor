import androidx.compose.ui.unit.sp
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

class RichTextStylesRestyleTest {

	private fun TestScope.newEditor(styles: RichTextStyles): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		state.richTextStyles = styles
		return state.withMarkdown(HammerMarkdownConfiguration)
	}

	private fun stylesWithBodySize(size: Float) = RichTextStyles.DEFAULT.copy(
		defaultTextStyle = RichTextStyles.DEFAULT.defaultTextStyle.copy(fontSize = size.sp),
		header1Style = RichTextStyles.DEFAULT.header1Style.copy(fontSize = (size * 2).sp),
	)

	@Test
	fun `stepping the font size leaves one baked style per heading`() = runTest {
		val editor = newEditor(stylesWithBodySize(16f))
		editor.importMarkdown("# Chapter One\n\nSome body text.")
		val before = editor.editorState.textLines[0].spanStyles.size

		repeat(5) { step -> editor.editorState.updateRichTextStyles(stylesWithBodySize(18f + step * 2)) }

		assertEquals(before, editor.editorState.textLines[0].spanStyles.size)
	}

	@Test
	fun `heading tracks the new font size`() = runTest {
		val editor = newEditor(stylesWithBodySize(16f))
		editor.importMarkdown("# Chapter One")

		editor.editorState.updateRichTextStyles(stylesWithBodySize(20f))

		val sizes = editor.editorState.textLines[0].spanStyles.map { it.item.fontSize }
		assertEquals(listOf(40f.sp), sizes.filter { it != 20f.sp })
	}
}
