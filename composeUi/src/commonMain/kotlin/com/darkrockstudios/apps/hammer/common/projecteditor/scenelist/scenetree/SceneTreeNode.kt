package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

/**
 * Composable wrapper that handles rendering individual nodes in the tree
 * as well as walking their children to be displayed.
 */
@Composable
fun SceneTreeNode(
    node: TreeValue<SceneItem>,
    selectedId: Int,
    draggableFactory: ((TreeValue<SceneItem>) -> Modifier)?,
    itemUi: ItemUi,
    collapsedNodes: HashMap<Int, Boolean>
) {
    var collapsed by remember(node.value.id) {
        mutableStateOf(
            collapsedNodes[node.value.id] ?: false
        )
    }

    val toggleExpanded = {
        val collapse = !(collapsedNodes[node.value.id] ?: false)
        collapsedNodes[node.value.id] = collapse
        collapsed = collapse
    }

    val dragModifier = draggableFactory?.invoke(node) ?: Modifier
    itemUi(node, toggleExpanded, dragModifier)

    node.children.forEach { child ->
        AnimatedVisibility(visible = !collapsed) {
            SceneTreeNode(
                node = child,
                selectedId = selectedId,
                draggableFactory = if (!collapsed) draggableFactory else null,
                itemUi = itemUi,
                collapsedNodes = collapsedNodes,
            )
        }
    }
}