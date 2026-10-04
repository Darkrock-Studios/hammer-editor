import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode.ReaderMode
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_EDIT_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_HEADINGS_TOGGLE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_INCREASE_FONT_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_LIST_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_NEXT_CHAPTER_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.READER_MODE_SCENE_BREAK_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.ReaderModeUi
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.readerSceneBodyTag
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.readerSceneHeadingTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ReaderModeUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val projectDef = ProjectDef(name = "Test", path = HPath(name = "Test", path = "/", isAbsolute = true))

	private fun item(id: Int, name: String, type: SceneItem.Type) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	private val chapterOne = item(1, "Arrival", SceneItem.Type.Group)
	private val chapterTwo = item(4, "The Dark Night", SceneItem.Type.Group)
	private val harbour = item(2, "The Harbour", SceneItem.Type.Scene)
	private val lampRoom = item(3, "The Lamp Room", SceneItem.Type.Scene)

	private val reader = FakeReader(
		ReaderMode.State(
			chapters = listOf(
				ReaderMode.ReaderChapter(chapterOne, sceneCount = 2),
				ReaderMode.ReaderChapter(chapterTwo, sceneCount = 0),
			),
			activeChapterId = chapterOne.id,
			scenes = listOf(
				ReaderMode.ReaderScene(harbour, "The keeper stepped off the ferry."),
				ReaderMode.ReaderScene(lampRoom, "One hundred and nine steps."),
			),
			isLoading = false,
		)
	)

	private fun showReader() {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.size(420.dp, 900.dp)) {
					ReaderModeUi(component = reader)
				}
			}
		}
	}

	@Test
	fun `Scenes are stitched with an ornament and no headings by default`() {
		showReader()

		compose.onNodeWithTag(readerSceneBodyTag(harbour.id)).assertTextEquals("The keeper stepped off the ferry.")
		compose.onNodeWithTag(readerSceneBodyTag(lampRoom.id)).assertTextEquals("One hundred and nine steps.")
		compose.onNodeWithTag(READER_MODE_SCENE_BREAK_TAG).assertIsDisplayed()
		compose.onNodeWithTag(readerSceneHeadingTag(harbour.id)).assertDoesNotExist()
	}

	@Test
	fun `Scene headings replace the ornament when enabled`() {
		showReader()

		compose.onNodeWithTag(READER_MODE_HEADINGS_TOGGLE_TAG).performClick()
		compose.waitForIdle()

		compose.onNodeWithTag(readerSceneHeadingTag(harbour.id)).assertIsDisplayed()
		compose.onNodeWithTag(readerSceneHeadingTag(lampRoom.id)).assertIsDisplayed()
		compose.onNodeWithTag(READER_MODE_SCENE_BREAK_TAG).assertDoesNotExist()
	}

	@Test
	fun `A scene heading opens that scene in the editor`() {
		reader.update { it.copy(showSceneHeadings = true) }
		showReader()

		compose.onNodeWithTag(readerSceneHeadingTag(lampRoom.id)).performClick()

		assertEquals(listOf<SceneItem?>(lampRoom), reader.edited)
	}

	@Test
	fun `Edit opens the scene being read`() {
		showReader()

		compose.onNodeWithTag(READER_MODE_EDIT_TAG).performClick()

		assertEquals(listOf<SceneItem?>(null), reader.edited)
	}

	@Test
	fun `The scene at the top of the column is reported as in view`() {
		showReader()
		compose.waitForIdle()

		assertEquals(harbour, reader.inView.last())
	}

	@Test
	fun `A scroll request is acknowledged once the chapter is on screen`() {
		reader.update { it.copy(scrollToSceneId = lampRoom.id) }
		showReader()
		compose.waitForIdle()

		assertEquals(1, reader.scrollsHandled)
	}

	@Test
	fun `Font size and chapter controls reach the component`() {
		showReader()

		compose.onNodeWithTag(READER_MODE_INCREASE_FONT_TAG).performClick()
		compose.onNodeWithTag(READER_MODE_LIST_TAG).performScrollToNode(hasTestTag(READER_MODE_NEXT_CHAPTER_TAG))
		compose.onNodeWithTag(READER_MODE_NEXT_CHAPTER_TAG).performClick()

		assertEquals(1, reader.fontIncreases)
		assertEquals(1, reader.nextChapters)
	}

	@Test
	fun `An empty chapter says so`() {
		reader.update { it.copy(activeChapterId = chapterTwo.id, scenes = emptyList()) }
		showReader()

		compose.onNodeWithText("This chapter has no scenes yet").assertIsDisplayed()
	}
}

private class FakeReader(initial: ReaderMode.State) : ReaderMode {
	override val state = MutableValue(initial)

	val edited = mutableListOf<SceneItem?>()
	val inView = mutableListOf<SceneItem?>()
	var scrollsHandled = 0
	var fontIncreases = 0
	var nextChapters = 0

	fun update(transform: (ReaderMode.State) -> ReaderMode.State) = state.update(transform)

	override fun showChapter(chapterId: Int) {}
	override fun nextChapter() {
		nextChapters++
	}

	override fun previousChapter() {}
	override fun showScene(sceneItem: SceneItem) {}
	override fun scrollHandled() {
		scrollsHandled++
		state.update { it.copy(scrollToSceneId = null) }
	}

	override fun sceneInView(sceneItem: SceneItem?) {
		inView.add(sceneItem)
	}

	override fun editScene(sceneItem: SceneItem?) {
		edited.add(sceneItem)
	}

	override fun toggleSceneHeadings() = state.update { it.copy(showSceneHeadings = !it.showSceneHeadings) }
	override fun increaseFontSize() {
		fontIncreases++
	}

	override fun decreaseFontSize() {}
	override fun resetFontSize() {}
	override fun setEditorMaxWidth(width: Float) {}
	override fun resetEditorMaxWidth() {}
	override fun close() {}
}
