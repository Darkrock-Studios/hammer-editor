package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree shape (id == global index):
 *
 * 0 Root
 * ├─ 1 Group A
 * │  ├─ 2 Scene A1
 * │  └─ 3 Group B
 * │     └─ 4 Scene B1
 * └─ 5 Group C
 *    └─ 6 Scene C1
 */
class SceneTreeVisibilityTest {

	private val projectDef = ProjectDef("Test", "/test".toPath().toHPath())

	@Test
	fun `expanded tree shows every node except the root scene`() {
		val visible = visibleSceneNodes(buildTree(), collapsedNodes = emptyMap())

		assertEquals(listOf(1, 2, 3, 4, 5, 6), visible.map { it.value.id })
	}

	@Test
	fun `collapsing a group hides its descendants but keeps the group itself`() {
		val visible = visibleSceneNodes(buildTree(), collapsedNodes = mapOf(1 to true))

		assertEquals(listOf(1, 5, 6), visible.map { it.value.id })
	}

	@Test
	fun `collapsing a nested group only hides that subtree`() {
		val visible = visibleSceneNodes(buildTree(), collapsedNodes = mapOf(3 to true))

		assertEquals(listOf(1, 2, 3, 5, 6), visible.map { it.value.id })
	}

	@Test
	fun `a group flagged but set to false stays expanded`() {
		val visible = visibleSceneNodes(buildTree(), collapsedNodes = mapOf(1 to false))

		assertEquals(listOf(1, 2, 3, 4, 5, 6), visible.map { it.value.id })
	}

	private fun buildTree(): ImmutableTree<SceneItem> {
		val tree = Tree<SceneItem>()
		tree.setRoot(
			node(
				item(0, SceneItem.Type.Root, ""),
				node(
					item(1, SceneItem.Type.Group, "A"),
					node(item(2, SceneItem.Type.Scene, "A1")),
					node(
						item(3, SceneItem.Type.Group, "B"),
						node(item(4, SceneItem.Type.Scene, "B1")),
					),
				),
				node(
					item(5, SceneItem.Type.Group, "C"),
					node(item(6, SceneItem.Type.Scene, "C1")),
				),
			)
		)
		return tree.toImmutableTree()
	}

	private fun item(id: Int, type: SceneItem.Type, name: String) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	private fun node(item: SceneItem, vararg children: TreeNode<SceneItem>): TreeNode<SceneItem> {
		val treeNode = TreeNode(item)
		children.forEach { treeNode.addChild(it) }
		return treeNode
	}
}
