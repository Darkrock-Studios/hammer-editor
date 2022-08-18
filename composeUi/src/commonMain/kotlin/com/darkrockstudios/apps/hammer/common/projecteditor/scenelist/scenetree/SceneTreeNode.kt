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
    parentCollapsed: Boolean,
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

    AnimatedVisibility(visible = !parentCollapsed) {
        val dragModifier = draggableFactory?.invoke(node) ?: Modifier
        itemUi(node, toggleExpanded, dragModifier)
    }

    node.children.forEach { child ->
        SceneTreeNode(
            node = child,
            selectedId = selectedId,
            parentCollapsed = (collapsed || parentCollapsed),
            draggableFactory = if (!collapsed) draggableFactory else null,
            itemUi = itemUi,
            collapsedNodes = collapsedNodes,
        )
    }
}