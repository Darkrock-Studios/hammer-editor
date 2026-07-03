package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

/**
 * The nodes the tree should actually render, in display order: every node except the root scene
 * and any node hidden beneath a collapsed ancestor. Collapsed subtrees are omitted entirely rather
 * than rendered as zero-height rows, which keeps the LazyColumn from composing the whole tree to
 * fill its viewport (the source of the bottom-row flicker on large, mostly-collapsed trees).
 */
internal fun visibleSceneNodes(
	tree: ImmutableTree<SceneItem>,
	collapsedNodes: Map<Int, Boolean>,
): List<TreeValue<SceneItem>> {
	val visible = ArrayList<TreeValue<SceneItem>>(tree.totalNodes)
	for (index in 0 until tree.totalNodes) {
		val node = tree[index]
		if (node.value.isRootScene) continue

		val hiddenByCollapsedAncestor = tree.getBranch(index, true)
			.any { collapsedNodes[it.value.id] == true }
		if (!hiddenByCollapsedAncestor) visible.add(node)
	}
	return visible
}

internal fun findInsertPosition(
	dragOffset: Offset,
	layouts: List<LazyListItemInfo>,
	collapsedGroups: SnapshotStateMap<Int, Boolean>,
	tree: ImmutableTree<SceneItem>,
	visibleNodes: List<TreeValue<SceneItem>>,
	selectedNode: TreeValue<SceneItem>?,
): InsertPosition? {
	if (selectedNode == null) return null
	val dragY = dragOffset.y

	val selectedId = selectedNode.value.id
	var foundItemId: InsertPosition? = null
	for (layout in layouts) {
		val id = layout.key as Int
		val size = layout.size
		val itemPos = layout.offset

		if (id != selectedId
			&& dragY >= itemPos
			&& dragY <= (itemPos + size)
		) {
			// Rendered rows come from visibleNodes; the tree search covers layout info
			// that is a frame behind a mid-drag tree update.
			val leaf = visibleNodes.find { it.value.id == id }
				?: tree.findBy { it.id == id }
				?: continue
			val isAncestorOf = tree.isAncestorOf(
				needleIndex = selectedNode.index,
				leafIndex = leaf.index
			)
			if (!isAncestorOf) {
				// Decide above or below
				val halfHeight = size / 2f
				val localY = dragY - itemPos
				val before = localY < halfHeight

				if (leaf.value.type == SceneItem.Type.Root) continue

				// Leaf is a group
				foundItemId = if (leaf.value.type.isCollection) {
					if (collapsedGroups[leaf.value.id] == true) {
						// Collapsed group: thirds — above, into, or below the group
						val third = size / 3f
						when {
							localY < third -> InsertPosition(tree.getCoordinatesFor(leaf), true)
							localY > third * 2 -> InsertPosition(
								tree.getCoordinatesFor(leaf),
								false
							)

							else -> firstPositionInGroup(tree, leaf)
						}
					}
					// Insert above group
					else if (before) {
						val coords = tree.getCoordinatesFor(leaf)
						InsertPosition(coords, true)
					}
					// Insert as first item in group
					else {
						firstPositionInGroup(tree, leaf)
					}
				}
				// Leaf is just a leaf
				else {
					val coords = tree.getCoordinatesFor(leaf)
					InsertPosition(coords, before)
				}

				break
			}
		}
	}
	return foundItemId
}

private fun firstPositionInGroup(
	tree: ImmutableTree<SceneItem>,
	group: TreeValue<SceneItem>,
): InsertPosition {
	return if (group.children.isNotEmpty()) {
		InsertPosition(tree.getCoordinatesFor(group.children[0]), true)
	} else {
		// Empty group - use -1 as sentinel for globalIndex
		val coords = NodeCoordinates(
			globalIndex = -1,
			parentIndex = group.index,
			childLocalIndex = 0
		)
		InsertPosition(coords, false)
	}
}