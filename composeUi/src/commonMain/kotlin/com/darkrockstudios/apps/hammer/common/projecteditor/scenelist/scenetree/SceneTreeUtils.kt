package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.NodeCoordinates
import io.github.aakira.napier.Napier

internal fun LayoutCoordinates.positionIn(ancestor: LayoutCoordinates): Offset {
    return ancestor.windowToLocal(positionInWindow())
}

internal fun findInsertPosition(
    dragOffset: Offset,
    layouts: HashMap<Int, LayoutCoordinates>,
    tree: ImmutableTree<SceneItem>,
    selectedId: Int,
    columnLayoutInfo: LayoutCoordinates
): InsertPosition? {
    val selectedItem = layouts[selectedId] ?: return null
    val selectedLocalPos = selectedItem.positionIn(columnLayoutInfo)

    val dragY = dragOffset.y + selectedLocalPos.y

    val selectedItemIndex = tree.indexOf { it.id == selectedId }

    var foundItemId: InsertPosition? = null
    for ((id, layout) in layouts.entries) {
        if (!layout.isAttached) continue

        val size = layout.size
        val itemPos = layout.positionIn(columnLayoutInfo)

        if (id != selectedId && dragY >= itemPos.y && dragY <= (itemPos.y + size.height)) {
            val leafGlobalIndex = tree.indexOf { it.id == id }
            val isAncestorOf = tree.isAncestorOf(
                needleIndex = selectedItemIndex,
                leafIndex = leafGlobalIndex
            )
            if (!isAncestorOf) {
                // Decide above or below
                val halfHeight = size.height / 2f
                val localY = dragY - itemPos.y
                val before = localY < halfHeight

                val leaf = tree[leafGlobalIndex]
                // Leaf is a group
                foundItemId = if (leaf.value.type.isCollection) {
                    // Insert above group
                    if (before) {
                        val coords = tree.getCoordinatesFor(leaf)
                        InsertPosition(coords, true)
                    }
                    // Insert as first item in group
                    else {
                        if (leaf.children.isNotEmpty()) {
                            val coords = tree.getCoordinatesFor(leaf.children[0])
                            Napier.d(coords.toString())
                            InsertPosition(coords, true)
                        } else {
                            val coords = NodeCoordinates(
                                globalIndex = leaf.index + 1,
                                parentIndex = leaf.index,
                                childLocalIndex = 0
                            )
                            Napier.d(coords.toString())
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