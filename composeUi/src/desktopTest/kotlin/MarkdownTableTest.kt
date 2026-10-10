import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.insertHorizontalRule
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.isTableCell
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableTest {

	private fun TestScope.newEditor(): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		return state.withMarkdown(HammerMarkdownConfiguration)
	}

	private val scene = "Before the table.\n\n| Name | Age |\n| --- | --: |\n| Ada | 36 |\n\nAfter the table."

	@Test
	fun `a table in a scene reads back as a table`() = runTest {
		val editor = newEditor()
		editor.importMarkdown(scene)

		val cells = editor.editorState.textLines.indices.filter { editor.editorState.isTableCell(it) }
		assertEquals(listOf("Name", "Age", "Ada", "36"), cells.map { editor.editorState.textLines[it].text })

		val reopened = newEditor()
		reopened.importMarkdown(editor.exportAsMarkdown())
		assertEquals(editor.exportAsMarkdown(), reopened.exportAsMarkdown())
	}

	@Test
	fun `a horizontal rule is not put in a table`() = runTest {
		val editor = newEditor()
		editor.importMarkdown(scene)
		val before = editor.exportAsMarkdown()
		val cell = editor.editorState.textLines.indices.first { editor.editorState.isTableCell(it) }

		editor.editorState.cursor.updatePosition(CharLineOffset(cell, 2))
		insertHorizontalRule(editor.editorState)

		assertTrue(editor.editorState.isTableCell(cell))
		assertEquals(before, editor.exportAsMarkdown())
	}
}
