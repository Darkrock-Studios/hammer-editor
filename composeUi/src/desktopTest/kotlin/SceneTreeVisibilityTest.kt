package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
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
			sceneNode(
				sceneItem(0, SceneItem.Type.Root, ""),
				sceneNode(
					sceneItem(1, SceneItem.Type.Group, "A"),
					sceneNode(sceneItem(2, SceneItem.Type.Scene, "A1")),
					sceneNode(
						sceneItem(3, SceneItem.Type.Group, "B"),
						sceneNode(sceneItem(4, SceneItem.Type.Scene, "B1")),
					),
				),
				sceneNode(
					sceneItem(5, SceneItem.Type.Group, "C"),
					sceneNode(sceneItem(6, SceneItem.Type.Scene, "C1")),
				),
			)
		)
		return tree.toImmutableTree()
	}
}
