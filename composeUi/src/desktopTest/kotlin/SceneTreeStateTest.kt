import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTreeState
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.shouldCollapseNode
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.visibleSceneTreeNodes
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneTreeStateTest {

	@Test
	fun `toggle expanded collapses and expands descendants`() {
		val state = sceneTreeState(sceneSummary())
		val childIndex = state.getTree().findBy { it.id == SCENE_A_ID }!!.index

		state.toggleExpanded(GROUP_A_ID)

		assertEquals(true, state.collapsedNodes[GROUP_A_ID])
		assertTrue(shouldCollapseNode(childIndex, state.summary, state.collapsedNodes))

		state.toggleExpanded(GROUP_A_ID)

		assertNull(state.collapsedNodes[GROUP_A_ID])
		assertFalse(shouldCollapseNode(childIndex, state.summary, state.collapsedNodes))
	}

	@Test
	fun `collapse all marks groups and expand all clears collapse state`() {
		val state = sceneTreeState(sceneSummary())

		state.collapseAll()

		assertEquals(true, state.collapsedNodes[GROUP_A_ID])
		assertEquals(true, state.collapsedNodes[GROUP_B_ID])
		assertNull(state.collapsedNodes[SCENE_A_ID])
		assertNull(state.collapsedNodes[SCENE_B_ID])

		state.expandAll()

		assertTrue(state.collapsedNodes.isEmpty())
	}

	@Test
	fun `deleted collapsed groups are pruned on summary update`() {
		val state = sceneTreeState(sceneSummary())

		state.toggleExpanded(GROUP_A_ID)
		state.updateSummary(sceneSummary(includeGroupA = false))

		assertNull(state.collapsedNodes[GROUP_A_ID])
	}

	@Test
	fun `visible scene tree nodes excludes descendants under collapsed ancestors`() {
		val state = sceneTreeState(sceneSummary())
		state.toggleExpanded(GROUP_A_ID)

		val visibleIds = visibleSceneTreeNodes(state.summary, state.collapsedNodes)
			.map { it.value.id }

		assertEquals(listOf(GROUP_A_ID, SCENE_C_ID), visibleIds)
		assertFalse(visibleIds.contains(SCENE_A_ID))
		assertFalse(visibleIds.contains(GROUP_B_ID))
		assertFalse(visibleIds.contains(SCENE_B_ID))
	}

	private fun sceneTreeState(summary: SceneSummary): SceneTreeState {
		return SceneTreeState(
			sceneSummary = summary,
			moveItem = {},
			coroutineScope = CoroutineScope(EmptyCoroutineContext),
			listState = LazyListState(),
			collapsedNodes = mutableStateMapOf<Int, Boolean>()
		)
	}

	private fun sceneSummary(includeGroupA: Boolean = true): SceneSummary {
		val tree = Tree<SceneItem>()
		val root = TreeNode(sceneItem(SceneItem.Type.Root, SceneItem.ROOT_ID, "Root"))
		tree.setRoot(root)

		if (includeGroupA) {
			val groupA = TreeNode(sceneItem(SceneItem.Type.Group, GROUP_A_ID, "Group A"))
			groupA.addChild(TreeNode(sceneItem(SceneItem.Type.Scene, SCENE_A_ID, "Scene A")))

			val groupB = TreeNode(sceneItem(SceneItem.Type.Group, GROUP_B_ID, "Group B"))
			groupB.addChild(TreeNode(sceneItem(SceneItem.Type.Scene, SCENE_B_ID, "Scene B")))
			groupA.addChild(groupB)

			root.addChild(groupA)
		}

		root.addChild(TreeNode(sceneItem(SceneItem.Type.Scene, SCENE_C_ID, "Scene C")))

		return SceneSummary(tree.toImmutableTree(), emptySet())
	}

	private fun sceneItem(type: SceneItem.Type, id: Int, name: String): SceneItem {
		return SceneItem(
			projectDef = PROJECT_DEF,
			type = type,
			id = id,
			name = name,
			order = id,
		)
	}

	private companion object {
		const val GROUP_A_ID = 1
		const val SCENE_A_ID = 2
		const val GROUP_B_ID = 3
		const val SCENE_B_ID = 4
		const val SCENE_C_ID = 5

		val PROJECT_DEF = ProjectDef(
			"Test Project",
			HPath("/projects/Test Project", "Test Project", true)
		)
	}
}
