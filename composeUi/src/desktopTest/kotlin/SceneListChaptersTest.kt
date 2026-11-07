import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_GROUP_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_SCENE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.sceneGroupTag
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.sceneItemTag
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test

private const val PROLOGUE_ID = 1
private const val ACT_ONE_ID = 2
private const val NESTED_SCENE_ID = 3

/** Top-level scenes and groups read as chapters; nested rows do not. */
class SceneListChaptersTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun showSceneList() {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.size(400.dp, 600.dp)) {
					SceneListUi(
						component = fakeComponent(),
						snackbarHostState = rememberRootSnackbarHostState(),
					)
				}
			}
		}
	}

	private fun chapterMark() = hasContentDescription("Chapter")

	@Test
	fun `a top-level scene is marked as a chapter`() {
		showSceneList()

		compose.onNodeWithTag(sceneItemTag(PROLOGUE_ID), useUnmergedTree = true)
			.onChildren().filterToOne(chapterMark()).assertIsDisplayed()
	}

	@Test
	fun `a top-level group is marked as a chapter`() {
		showSceneList()

		compose.onNodeWithTag(sceneGroupTag(ACT_ONE_ID), useUnmergedTree = true)
			.onChildren().filterToOne(chapterMark()).assertIsDisplayed()
	}

	@Test
	fun `a nested scene carries no chapter mark`() {
		showSceneList()

		compose.onNodeWithTag(sceneItemTag(NESTED_SCENE_ID), useUnmergedTree = true)
			.onChildren().filterToOne(chapterMark()).assertDoesNotExist()
	}

	@Test
	fun `header counts chapters and scenes`() {
		showSceneList()

		compose.onNodeWithText("2 CH · 2 SCN").assertIsDisplayed()
	}

	@Test
	fun `root add menu creates chapters`() {
		showSceneList()

		compose.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).performClick()

		compose.onNodeWithTag(SCENE_LIST_ADD_SCENE_TAG).assertIsDisplayed()
		compose.onNodeWithText("New Chapter Scene").assertIsDisplayed()
		compose.onNodeWithTag(SCENE_LIST_ADD_GROUP_TAG).assertIsDisplayed()
		compose.onNodeWithText("New Chapter Group").assertIsDisplayed()
	}

	@Test
	fun `creating a chapter group from the root titles the dialog as a chapter`() {
		showSceneList()

		compose.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).performClick()
		compose.onNodeWithTag(SCENE_LIST_ADD_GROUP_TAG).performClick()

		compose.onNodeWithText("Create Chapter Group").assertIsDisplayed()
		compose.onNodeWithText("Chapter Name").assertIsDisplayed()
	}
}

private fun testScene(id: Int, name: String, type: SceneItem.Type = SceneItem.Type.Scene) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = name,
	order = id,
)

private fun testSceneSummary(): SceneSummary {
	val tree = Tree<SceneItem>()
	val root = TreeNode(testScene(0, "Root", SceneItem.Type.Root))
	root.addChild(TreeNode(testScene(PROLOGUE_ID, "Prologue")))
	root.addChild(
		TreeNode(testScene(ACT_ONE_ID, "Act One", SceneItem.Type.Group)).apply {
			addChild(TreeNode(testScene(NESTED_SCENE_ID, "Arrival")))
		}
	)
	tree.setRoot(root)

	return SceneSummary(tree.toImmutableTree(), persistentSetOf())
}

private fun fakeComponent() = object : SceneList {
	override val state: Value<SceneList.State> = MutableValue(
		SceneList.State(
			projectDef = fakeProjectDef(),
			sceneSummary = testSceneSummary(),
		)
	)

	override fun onSceneSelected(sceneDef: SceneItem) {}
	override suspend fun moveScene(moveRequest: MoveRequest) {}
	override fun loadScenes() {}
	override suspend fun createScene(parent: SceneItem?, sceneName: String) {}
	override suspend fun createGroup(parent: SceneItem?, groupName: String) {}
	override suspend fun deleteScene(scene: SceneItem) {}
	override suspend fun renameScene(scene: SceneItem, newName: String): Boolean = false
	override fun onSceneListUpdate(scenes: SceneSummary) {}
	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {}
	override fun showOutlineOverview() {}
	override fun showReaderMode() {}
	override suspend fun archiveScene(scene: SceneItem) {}
	override suspend fun unarchiveScene(scene: SceneItem) {}
	override fun showArchivedScenes() {}
	override fun dismissArchivedDialog() {}
}
