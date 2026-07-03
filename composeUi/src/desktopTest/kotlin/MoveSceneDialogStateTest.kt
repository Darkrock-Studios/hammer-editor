package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.sceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.sceneNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree shape (id == global index):
 *
 * 0 Root
 * ├─ 1 Group A
 * │  ├─ 2 Scene A1
 * │  ├─ 3 Scene A2   <- item under test, position 2 of 3
 * │  └─ 4 Scene A3
 * ├─ 5 Group B
 * │  └─ 6 Scene B1
 * └─ 7 Scene X
 */
class MoveSceneDialogStateTest {

	private fun buildTree(
		includeGroupB: Boolean = true,
		extraRootScene: Boolean = false
	): ImmutableTree<SceneItem> {
		val tree = Tree<SceneItem>()
		val root = sceneNode(
			sceneItem(0, SceneItem.Type.Root, ""),
			sceneNode(
				sceneItem(1, SceneItem.Type.Group, "A"),
				sceneNode(sceneItem(2, SceneItem.Type.Scene, "A1")),
				sceneNode(sceneItem(3, SceneItem.Type.Scene, "A2")),
				sceneNode(sceneItem(4, SceneItem.Type.Scene, "A3")),
			),
		)
		if (includeGroupB) {
			root.addChild(
				sceneNode(
					sceneItem(5, SceneItem.Type.Group, "B"),
					sceneNode(sceneItem(6, SceneItem.Type.Scene, "B1")),
				)
			)
		}
		root.addChild(sceneNode(sceneItem(7, SceneItem.Type.Scene, "X")))
		if (extraRootScene) {
			root.addChild(sceneNode(sceneItem(8, SceneItem.Type.Scene, "Y")))
		}
		tree.setRoot(root)
		return tree.toImmutableTree()
	}

	private fun stateFor(
		itemId: Int,
		tree: ImmutableTree<SceneItem> = buildTree()
	): MoveSceneDialogState {
		val item = tree.findBy { it.id == itemId }!!.value
		return MoveSceneDialogState(item, tree)
	}

	@Test
	fun `Defaults to the current parent and the item's current slot`() {
		val state = stateFor(itemId = 3)

		assertEquals(1, state.selectedDestId)
		assertEquals("2", state.positionText)
	}

	@Test
	fun `Untouched dialog builds a no-op request position`() {
		val state = stateFor(itemId = 3)
		val tree = state.tree

		val request = state.buildRequest()!!
		// Insert index 2 with before=true resolves to final index 1 — the item's current slot.
		assertEquals(3, request.id)
		assertEquals(tree.findBy { it.id == 1 }!!.index, request.toPosition.coords.parentIndex)
		assertEquals(2, request.toPosition.coords.childLocalIndex)
	}

	@Test
	fun `Selecting a different group defaults to its last slot`() {
		val state = stateFor(itemId = 3)

		state.select(5)

		assertEquals(2, state.maxPosition)
		assertEquals("2", state.positionText)
	}

	@Test
	fun `Re-selecting the current parent restores the current slot`() {
		val state = stateFor(itemId = 3)

		state.select(5)
		state.select(1)

		assertEquals("2", state.positionText)
	}

	@Test
	fun `Tree update preserves a still-valid selection`() {
		val state = stateFor(itemId = 3)
		state.select(5)
		state.query = "B"
		state.positionText = "1"

		state.updateTree(buildTree(extraRootScene = true))

		assertEquals(5, state.selectedDestId)
		assertEquals("B", state.query)
		assertEquals("1", state.positionText)
	}

	@Test
	fun `Tree update resets a removed destination to the current parent`() {
		val state = stateFor(itemId = 3)
		state.select(5)
		state.positionText = "1"

		state.updateTree(buildTree(includeGroupB = false))

		assertEquals(1, state.selectedDestId)
		assertEquals("2", state.positionText)
	}
}
