package components.storyeditor

import com.darkrockstudios.apps.hammer.common.components.storyeditor.firstSceneOfNextGroup
import com.darkrockstudios.apps.hammer.common.components.storyeditor.firstSceneOfPreviousGroup
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneAfter
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneBefore
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SceneOrderTest {

	private val projectDef = ProjectDef(testProjectId("Order"), "Order", HPath("/projects/Order", "Order", false))

	private fun node(id: Int, type: SceneItem.Type = SceneItem.Type.Scene, vararg children: TreeNode<SceneItem>) =
		TreeNode(SceneItem(projectDef, type, id, "Item $id", order = 0)).apply { children.forEach(::addChild) }

	// root -> [S1, G1 -> [S2, S3], G2 -> [S4], S5]; group ids are offset so they never match a scene id.
	private val tree = Tree<SceneItem>().apply {
		setRoot(
			node(
				0, SceneItem.Type.Root,
				node(1),
				node(101, SceneItem.Type.Group, node(2), node(3)),
				node(102, SceneItem.Type.Group, node(4)),
				node(5),
			)
		)
	}.toImmutableTree()

	@Test
	fun `next and previous follow tree order across groups`() {
		assertEquals(2, tree.sceneAfter(1)?.id)
		assertEquals(4, tree.sceneAfter(3)?.id)
		assertNull(tree.sceneAfter(5))
		assertEquals(3, tree.sceneBefore(4)?.id)
		assertNull(tree.sceneBefore(1))
	}

	@Test
	fun `next group starts at its first scene`() {
		assertEquals(2, tree.firstSceneOfNextGroup(1)?.id)
		assertEquals(4, tree.firstSceneOfNextGroup(2)?.id)
		assertEquals(5, tree.firstSceneOfNextGroup(4)?.id)
		assertNull(tree.firstSceneOfNextGroup(5))
	}

	@Test
	fun `previous group starts at its first scene`() {
		assertEquals(2, tree.firstSceneOfPreviousGroup(4)?.id)
		assertEquals(1, tree.firstSceneOfPreviousGroup(3)?.id)
		assertNull(tree.firstSceneOfPreviousGroup(1))
	}

	@Test
	fun `an id that is not a scene in the tree has no neighbours`() {
		assertNull(tree.sceneAfter(101))
		assertNull(tree.sceneBefore(99))
		assertNull(tree.firstSceneOfNextGroup(99))
		assertNull(tree.firstSceneOfPreviousGroup(101))
	}
}
