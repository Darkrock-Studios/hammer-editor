import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.loadSceneContent
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.sceneDiffText
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.HorizontalRuleSpanStyle
import com.darkrockstudios.texteditor.state.TextEditorState
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SceneContentLoadTest {

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = HPath(path = "/test", name = "Test Project", isAbsolute = true),
	)

	private val sceneItem = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = 1,
		name = "Test Scene",
		order = 0,
	)

	private fun TestScope.newEditor(): MarkdownExtension {
		val state = TextEditorState(scope = this, measurer = mockk(relaxed = true))
		return MarkdownExtension(state, MarkdownConfiguration.DEFAULT)
	}

	private fun MarkdownExtension.horizontalRuleLines(): List<Int> =
		editorState.richSpanManager.getAllRichSpans()
			.filter { it.style === HorizontalRuleSpanStyle }
			.map { it.range.start.line }
			.sorted()

	@Test
	fun `horizontal rule survives reloading a live editor buffer`() = runTest {
		val original = "Before the break.\n\n---\n\nAfter the break."

		val editing = newEditor()
		editing.importMarkdown(original)
		val buffered = SceneContent(
			scene = sceneItem,
			platformRepresentation = ComposeRichText(editing),
		)

		val reopened = newEditor()
		loadSceneContent(reopened, buffered)

		assertEquals(listOf(2), reopened.horizontalRuleLines())
		assertEquals(original, reopened.exportAsMarkdown())
	}

	@Test
	fun `horizontal rule survives loading markdown from disk`() = runTest {
		val original = "Before the break.\n\n---\n\nAfter the break."
		val onDisk = SceneContent(scene = sceneItem, markdown = original)

		val reopened = newEditor()
		loadSceneContent(reopened, onDisk)

		assertEquals(listOf(2), reopened.horizontalRuleLines())
		assertEquals(original, reopened.exportAsMarkdown())
	}

	@Test
	fun `null content loads an empty document`() = runTest {
		val editor = newEditor()
		editor.importMarkdown("Some prior text")

		loadSceneContent(editor, null)

		assertEquals("", editor.exportAsMarkdown())
	}

	@Test
	fun `diff text distinguishes a horizontal rule from a blank line`() = runTest {
		val withRule = newEditor()
		loadSceneContent(withRule, SceneContent(sceneItem, "Before.\n\n---\n\nAfter."))
		val withoutRule = newEditor()
		loadSceneContent(withoutRule, SceneContent(sceneItem, "Before.\n\n\n\nAfter."))

		assertNotEquals(sceneDiffText(withRule), sceneDiffText(withoutRule))
	}

	@Test
	fun `diff text stays in the editor coordinate space`() = runTest {
		val editor = newEditor()
		loadSceneContent(editor, SceneContent(sceneItem, "Before.\n\n---\n\nAfter."))

		// The diff's offsets index back into the editor, so substitution must not resize.
		assertEquals(editor.editorState.getAllText().text.length, sceneDiffText(editor).length)
	}

	@Test
	fun `diff text leaves prose untouched`() = runTest {
		val editor = newEditor()
		loadSceneContent(editor, SceneContent(sceneItem, "Just prose.\n\nNo blocks here."))

		assertEquals(editor.editorState.getAllText().text, sceneDiffText(editor))
	}
}
