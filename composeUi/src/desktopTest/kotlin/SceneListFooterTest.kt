import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_TREE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.sceneItemTag
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

private const val PANE_TAG = "test-pane"
private const val SCENE_COUNT = 40

class SceneListFooterTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun showSceneList(paneHeight: Dp = 600.dp) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.testTag(PANE_TAG).size(400.dp, paneHeight)) {
					SceneListUi(
						component = fakeComponent(),
						snackbarHostState = rememberRootSnackbarHostState(),
					)
				}
			}
		}
	}

	private fun SemanticsNodeInteraction.bounds() = fetchSemanticsNode().boundsInRoot

	@Test
	fun `Scene list fills the pane beneath the footer`() {
		showSceneList()

		val pane = compose.onNodeWithTag(PANE_TAG).bounds()
		val tree = compose.onNodeWithTag(SCENE_LIST_TREE_TAG).bounds()

		assertTrue(
			tree.bottom >= pane.bottom - 1f,
			"Footer must overlay the list, not shrink it: tree bottom ${tree.bottom}, pane bottom ${pane.bottom}",
		)
	}

	@Test
	fun `Last scene clears the footer when scrolled to the bottom`() {
		showSceneList()

		compose.onNodeWithTag(SCENE_LIST_TREE_TAG).performScrollToIndex(SCENE_COUNT - 1)

		val footer = compose.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).bounds()
		val lastScene = compose.onNodeWithTag(sceneItemTag(SCENE_COUNT)).bounds()

		assertTrue(
			lastScene.bottom <= footer.top + 1f,
			"Last scene must scroll clear of the footer: scene bottom ${lastScene.bottom}, footer top ${footer.top}",
		)
	}

	@Test
	fun `Footer stays visible at the bottom of the list`() {
		showSceneList()

		compose.onNodeWithTag(SCENE_LIST_TREE_TAG).performScrollToIndex(SCENE_COUNT - 1)

		compose.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).assertIsDisplayed()
	}
}

private fun testProjectDef() = ProjectDef(
	name = "Test",
	path = HPath(name = "Test", path = "/", isAbsolute = true),
)

private fun testScene(id: Int, order: Int, type: SceneItem.Type = SceneItem.Type.Scene) = SceneItem(
	projectDef = testProjectDef(),
	type = type,
	id = id,
	name = "Test Scene $id",
	order = order,
)

private fun testSceneSummary(): SceneSummary {
	val tree = Tree<SceneItem>()
	val root = TreeNode(testScene(0, 0, SceneItem.Type.Root))
	for (i in 1..SCENE_COUNT) {
		root.addChild(TreeNode(testScene(i, i - 1)))
	}
	tree.setRoot(root)

	return SceneSummary(tree.toImmutableTree(), persistentSetOf())
}

private fun fakeComponent() = object : SceneList {
	override val state: Value<SceneList.State> = MutableValue(
		SceneList.State(
			projectDef = testProjectDef(),
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
	override suspend fun archiveScene(scene: SceneItem) {}
	override suspend fun unarchiveScene(scene: SceneItem) {}
	override fun showArchivedScenes() {}
	override fun dismissArchivedDialog() {}
}
