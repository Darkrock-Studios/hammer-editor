import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.state.TextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class MarkdownConfigRestyleTest {

	private fun TestScope.newEditor(config: MarkdownConfiguration): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		return MarkdownExtension(state, config)
	}

	private fun configWithBodySize(size: Float) = MarkdownConfiguration.DEFAULT.copy(
		defaultTextStyle = MarkdownConfiguration.DEFAULT.defaultTextStyle.copy(fontSize = size.sp),
		header1Style = MarkdownConfiguration.DEFAULT.header1Style.copy(fontSize = (size * 2).sp),
	)

	@Test
	fun `stepping the font size leaves one baked style per heading`() = runTest {
		val editor = newEditor(configWithBodySize(16f))
		editor.importMarkdown("# Chapter One\n\nSome body text.")
		val before = editor.editorState.textLines[0].spanStyles.size

		repeat(5) { step -> editor.updateMarkdownConfiguration(configWithBodySize(18f + step * 2)) }

		assertEquals(before, editor.editorState.textLines[0].spanStyles.size)
	}

	@Test
	fun `heading tracks the new font size`() = runTest {
		val editor = newEditor(configWithBodySize(16f))
		editor.importMarkdown("# Chapter One")

		editor.updateMarkdownConfiguration(configWithBodySize(20f))

		val sizes = editor.editorState.textLines[0].spanStyles.map { it.item.fontSize }
		assertEquals(listOf(40f.sp), sizes.filter { it != 20f.sp })
	}
}
