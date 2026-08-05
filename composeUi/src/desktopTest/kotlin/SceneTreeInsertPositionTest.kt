package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tree shape (id == global index):
 *
 * 0 Root
 * ├─ 1 Group A
 * │  ├─ 2 Scene A1
 * │  └─ 3 Group B
 * │     └─ 4 Scene B1
 * ├─ 5 Group C (empty)
 * └─ 6 Scene X
 */
class SceneTreeInsertPositionTest {

	private val rowHeight = 30f

	private fun dropAt(
		tree: ImmutableTree<SceneItem>,
		collapsed: SnapshotStateMap<Int, Boolean>,
		draggedId: Int,
		targetId: Int,
		fractionIntoRow: Float,
	): InsertPosition? {
		val visibleNodes = visibleSceneNodes(tree, collapsed)
		val layouts = visibleNodes.mapIndexed { index, node ->
			RowLayout(
				id = node.value.id,
				top = index * rowHeight,
				height = rowHeight,
			)
		}
		val targetLayout = layouts.first { it.id == targetId }
		val dragY = targetLayout.top + (rowHeight * fractionIntoRow)
		return findInsertPosition(
			dragOffset = Offset(0f, dragY),
			layouts = layouts,
			collapsedGroups = collapsed,
			tree = tree,
			visibleNodes = visibleNodes,
			selectedNode = tree.findBy { it.id == draggedId },
		)
	}

	@Test
	fun `expanded group, top half inserts before the group`() {
		val pos = dropAt(
			buildTree(),
			mutableStateMapOf(),
			draggedId = 6,
			targetId = 1,
			fractionIntoRow = 0.25f
		)

		assertNotNull(pos)
		assertEquals(1, pos.coords.globalIndex)
		assertEquals(true, pos.before)
	}

	@Test
	fun `expanded group, bottom half inserts at first position inside`() {
		val pos = dropAt(
			buildTree(),
			mutableStateMapOf(),
			draggedId = 6,
			targetId = 1,
			fractionIntoRow = 0.75f
		)

		assertNotNull(pos)
		assertEquals(2, pos.coords.globalIndex)
		assertEquals(1, pos.coords.parentIndex)
		assertEquals(0, pos.coords.childLocalIndex)
		assertEquals(true, pos.before)
	}

	@Test
	fun `collapsed group, top third inserts before the group`() {
		val collapsed = mutableStateMapOf(1 to true)
		val pos =
			dropAt(buildTree(), collapsed, draggedId = 6, targetId = 1, fractionIntoRow = 0.15f)

		assertNotNull(pos)
		assertEquals(1, pos.coords.globalIndex)
		assertEquals(true, pos.before)
	}

	@Test
	fun `collapsed group, middle third inserts at first position inside`() {
		val collapsed = mutableStateMapOf(1 to true)
		val pos =
			dropAt(buildTree(), collapsed, draggedId = 6, targetId = 1, fractionIntoRow = 0.5f)

		assertNotNull(pos)
		assertEquals(2, pos.coords.globalIndex)
		assertEquals(1, pos.coords.parentIndex)
		assertEquals(0, pos.coords.childLocalIndex)
		assertEquals(true, pos.before)
	}

	@Test
	fun `collapsed group, bottom third inserts after the group`() {
		val collapsed = mutableStateMapOf(1 to true)
		val pos =
			dropAt(buildTree(), collapsed, draggedId = 6, targetId = 1, fractionIntoRow = 0.85f)

		assertNotNull(pos)
		assertEquals(1, pos.coords.globalIndex)
		assertEquals(false, pos.before)
	}

	@Test
	fun `empty group, bottom half uses the empty-group sentinel`() {
		val pos = dropAt(
			buildTree(),
			mutableStateMapOf(),
			draggedId = 6,
			targetId = 5,
			fractionIntoRow = 0.75f
		)

		assertNotNull(pos)
		assertEquals(-1, pos.coords.globalIndex)
		assertEquals(5, pos.coords.parentIndex)
		assertEquals(0, pos.coords.childLocalIndex)
	}

	@Test
	fun `collapsed empty group, middle third uses the empty-group sentinel`() {
		val collapsed = mutableStateMapOf(5 to true)
		val pos =
			dropAt(buildTree(), collapsed, draggedId = 6, targetId = 5, fractionIntoRow = 0.5f)

		assertNotNull(pos)
		assertEquals(-1, pos.coords.globalIndex)
		assertEquals(5, pos.coords.parentIndex)
		assertEquals(0, pos.coords.childLocalIndex)
	}

	@Test
	fun `dropping a group onto its own descendant is rejected`() {
		val pos = dropAt(
			buildTree(),
			mutableStateMapOf(),
			draggedId = 1,
			targetId = 3,
			fractionIntoRow = 0.5f
		)

		assertEquals(null, pos)
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
				sceneNode(sceneItem(5, SceneItem.Type.Group, "C")),
				sceneNode(sceneItem(6, SceneItem.Type.Scene, "X")),
			)
		)
		return tree.toImmutableTree()
	}
}
