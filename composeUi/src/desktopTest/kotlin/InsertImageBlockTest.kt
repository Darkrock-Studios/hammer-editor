import androidx.compose.ui.graphics.ImageBitmap
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.insertImageBlock
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.reconcileImageBlocks
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.richstyle.IMAGE_PLACEHOLDER
import com.darkrockstudios.texteditor.richstyle.ImageBlockSpanStyle
import com.darkrockstudios.texteditor.richstyle.InMemoryImageProvider
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.isTableCell
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertImageBlockTest {

	private val provider = InMemoryImageProvider().apply { put("hammer://image/3", ImageBitmap(4, 3)) }

	private fun TestScope.newEditor(): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		return state.withMarkdown(HammerMarkdownConfiguration, provider)
	}

	private fun MarkdownExtension.imageSpans() =
		editorState.richSpanManager.getAllRichSpans().filter { it.style is ImageBlockSpanStyle }

	@Test
	fun `an inserted image saves as an image reference`() = runTest {
		val editor = newEditor()

		assertTrue(insertImageBlock(editor.editorState, provider, imageId = 3))

		assertEquals("![image](hammer://image/3)", editor.exportAsMarkdown().trim())
		assertEquals(1, editor.editorState.cursorPosition.line)
	}

	@Test
	fun `an image inserted mid line goes on a line of its own`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Before after")
		editor.editorState.cursor.updatePosition(CharLineOffset(0, 7))

		insertImageBlock(editor.editorState, provider, imageId = 3)

		val lines = editor.exportAsMarkdown().lines().filter { it.isNotBlank() }.map { it.trimEnd() }
		assertEquals(listOf("Before", "![image](hammer://image/3)", "after"), lines)
	}

	@Test
	fun `a saved image reads back as an image block`() = runTest {
		val editor = newEditor()
		insertImageBlock(editor.editorState, provider, imageId = 3)
		val saved = editor.exportAsMarkdown()

		val reopened = newEditor()
		reopened.importMarkdown(saved)

		val style = reopened.imageSpans().single().style as ImageBlockSpanStyle
		assertEquals("hammer://image/3", style.source)
		assertEquals("image", style.alt)
		assertEquals(saved, reopened.exportAsMarkdown())
	}

	@Test
	fun `existing alt text with markdown syntax survives the round trip`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("""![Cat \[the \*best\*\]](hammer://image/3)""")
		val saved = editor.exportAsMarkdown()

		val reopened = newEditor()
		reopened.importMarkdown(saved)

		assertEquals("Cat [the *best*]", (reopened.imageSpans().single().style as ImageBlockSpanStyle).alt)
		assertEquals(saved, reopened.exportAsMarkdown())
	}

	@Test
	fun `an image is not put in a table cell`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("| Name | Age |\n| --- | --- |\n| Ada | 36 |")
		val before = editor.exportAsMarkdown()
		val cell = editor.editorState.textLines.indices.first { editor.editorState.isTableCell(it) }
		editor.editorState.cursor.updatePosition(CharLineOffset(cell, 1))

		assertFalse(insertImageBlock(editor.editorState, provider, imageId = 3))

		assertEquals(before, editor.exportAsMarkdown())
		assertTrue(editor.imageSpans().isEmpty())
	}

	@Test
	fun `text typed on the image line moves to its own line`() = runTest {
		val editor = newEditor()
		insertImageBlock(editor.editorState, provider, imageId = 3)
		val imageLine = editor.imageSpans().single().range.start.line

		editor.editorState.cursor.updatePosition(CharLineOffset(imageLine, 1))
		editor.editorState.insertStringAtCursor("after")
		reconcileImageBlocks(editor.editorState)

		val lines = editor.exportAsMarkdown().lines().filter { it.isNotBlank() }
		assertEquals(listOf("![image](hammer://image/3)", "after"), lines)
	}

	@Test
	fun `text typed before the image moves to its own line`() = runTest {
		val editor = newEditor()
		insertImageBlock(editor.editorState, provider, imageId = 3)
		val imageLine = editor.imageSpans().single().range.start.line

		editor.editorState.cursor.updatePosition(CharLineOffset(imageLine, 0))
		editor.editorState.insertStringAtCursor("before")
		reconcileImageBlocks(editor.editorState)

		val lines = editor.exportAsMarkdown().lines().filter { it.isNotBlank() }
		assertEquals(listOf("before", "![image](hammer://image/3)"), lines)
	}

	@Test
	fun `deleting the image placeholder removes the image`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Above\n\n![Cat](hammer://image/3)\n\nBelow")
		val imageLine = editor.imageSpans().single().range.start.line

		editor.editorState.cursor.updatePosition(CharLineOffset(imageLine, 1))
		editor.editorState.backspaceAtCursor()
		reconcileImageBlocks(editor.editorState)

		assertTrue(editor.imageSpans().isEmpty())
		assertFalse("hammer://image" in editor.exportAsMarkdown())
	}

	@Test
	fun `text pasted over the placeholder removes the image and stays as typed`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Above\n\n![Cat](hammer://image/3)\n\nBelow")
		val imageLine = editor.imageSpans().single().range.start.line

		editor.editorState.replace(
			TextEditorRange(CharLineOffset(imageLine, 0), CharLineOffset(imageLine, IMAGE_PLACEHOLDER.length)),
			"hello world",
		)
		reconcileImageBlocks(editor.editorState)

		assertTrue(editor.imageSpans().isEmpty())
		assertEquals("hello world", editor.editorState.textLines[imageLine].text)
		assertFalse("hammer://image" in editor.exportAsMarkdown())
	}

	@Test
	fun `text typed after an intact placeholder moves to its own line and keeps the image`() = runTest {
		val editor = newEditor()
		insertImageBlock(editor.editorState, provider, imageId = 3)
		val imageLine = editor.imageSpans().single().range.start.line

		editor.editorState.cursor.updatePosition(CharLineOffset(imageLine, IMAGE_PLACEHOLDER.length))
		editor.editorState.insertStringAtCursor("abc")
		reconcileImageBlocks(editor.editorState)

		val span = editor.imageSpans().single()
		assertEquals(IMAGE_PLACEHOLDER, editor.editorState.textLines[span.range.start.line].text)
		assertEquals("abc", editor.editorState.textLines[span.range.start.line + 1].text)
	}

	@Test
	fun `images in lists and quotes are left as they are`() = runTest {
		val markdown = "1. ![Shot](hammer://image/3)\n\n> ![Quote](hammer://image/3)\n\n- ![Item](hammer://image/3)"
		val editor = newEditor()
		editor.importMarkdown(markdown)
		val before = editor.exportAsMarkdown()
		assertEquals(3, editor.imageSpans().size)

		reconcileImageBlocks(editor.editorState)

		assertEquals(3, editor.imageSpans().size)
		assertEquals(before, editor.exportAsMarkdown())
	}
}
