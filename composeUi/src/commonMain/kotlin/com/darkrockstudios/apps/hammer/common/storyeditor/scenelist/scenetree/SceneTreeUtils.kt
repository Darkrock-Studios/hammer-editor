package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue


internal fun findInsertPosition(
	dragOffset: Offset,
	layouts: List<LazyListItemInfo>,
	collapsedGroups: SnapshotStateMap<Int, Boolean>,
	tree: ImmutableTree<SceneItem>,
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
			val leaf = tree.findBy { it.id == id } ?: continue
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
					// Insert above group
					if (before) {
						val coords = tree.getCoordinatesFor(leaf)
						InsertPosition(coords, true)
					}
					// Insert as first item in group
					else {
						if (collapsedGroups[leaf.value.id] == true) {
							val coords = tree.getCoordinatesFor(leaf)
							InsertPosition(coords, false)
						} else if (leaf.children.isNotEmpty()) {
							val coords = tree.getCoordinatesFor(leaf.children[0])
							InsertPosition(coords, true)
						} else {
							// Empty group - use -1 as sentinel for globalIndex
							val coords = NodeCoordinates(
								globalIndex = -1,
								parentIndex = leaf.index,
								childLocalIndex = 0
							)
							InsertPosition(coords, false)
						}
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