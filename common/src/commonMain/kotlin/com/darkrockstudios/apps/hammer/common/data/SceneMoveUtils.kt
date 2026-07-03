package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

/**
 * All groups (plus the root) that [item] could legally be moved into: every collection node
 * except [item] itself and anything inside its own subtree.
 */
fun validMoveDestinations(
	tree: ImmutableTree<SceneItem>,
	item: SceneItem,
): List<TreeValue<SceneItem>> {
	val itemNode = tree.findBy { it.id == item.id } ?: return emptyList()
	return tree.filter { node -> isValidDestination(tree, itemNode, node) }
}

/**
 * How many positions [item] can occupy under [destParentId]: the current sibling count when
 * reordering in place, or child count + 1 when moving to a different parent. Returns null when
 * the destination is invalid for [item].
 */
fun movePositionCount(
	tree: ImmutableTree<SceneItem>,
	item: SceneItem,
	destParentId: Int,
): Int? {
	val itemNode = tree.findBy { it.id == item.id } ?: return null
	val destParent = tree.findBy { it.id == destParentId } ?: return null
	if (!isValidDestination(tree, itemNode, destParent)) return null
	return positionCount(itemNode, destParent)
}

/**
 * Builds the [MoveRequest] that places [item] at [destIndex] among the children of
 * [destParentId], where [destIndex] is the desired final position (0-based) after the move.
 *
 * [destIndex] is coerced into the range given by [movePositionCount]. Returns null if the item
 * or destination doesn't exist, the destination isn't a collection, or it lies inside the
 * item's own subtree.
 */
fun computeMoveRequest(
	tree: ImmutableTree<SceneItem>,
	item: SceneItem,
	destParentId: Int,
	destIndex: Int,
): MoveRequest? {
	val itemNode = tree.findBy { it.id == item.id } ?: return null
	val destParent = tree.findBy { it.id == destParentId } ?: return null
	if (!isValidDestination(tree, itemNode, destParent)) return null

	val target = destIndex.coerceIn(0, positionCount(itemNode, destParent) - 1)

	// insertChild() removes the item before re-inserting, so for a same-parent move the
	// repository interprets childLocalIndex against the pre-removal list and compensates.
	// Mirror that math here so destIndex is always the item's final resting position.
	val insertIndex = if (itemNode.parent == destParent.index) {
		val fromIndex = destParent.children.indexOfFirst { it.index == itemNode.index }
		if (fromIndex <= target) target + 1 else target
	} else {
		target
	}

	val coords = NodeCoordinates(
		globalIndex = destParent.children.getOrNull(insertIndex)?.index ?: -1,
		parentIndex = destParent.index,
		childLocalIndex = insertIndex,
	)
	return MoveRequest(item.id, InsertPosition(coords, before = true))
}

private fun isValidDestination(
	tree: ImmutableTree<SceneItem>,
	itemNode: TreeValue<SceneItem>,
	destParent: TreeValue<SceneItem>,
): Boolean =
	destParent.value.type.isCollection &&
		destParent.index != itemNode.index &&
		!tree.isAncestorOf(needleIndex = itemNode.index, leafIndex = destParent.index)

private fun positionCount(
	itemNode: TreeValue<SceneItem>,
	destParent: TreeValue<SceneItem>,
): Int = if (itemNode.parent == destParent.index) {
	destParent.children.size
} else {
	destParent.children.size + 1
}
