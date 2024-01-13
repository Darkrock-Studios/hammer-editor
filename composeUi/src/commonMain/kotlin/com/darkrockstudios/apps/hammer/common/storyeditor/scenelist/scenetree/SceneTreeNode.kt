package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

/**
 * Composable wrapper that handles rendering individual nodes in the tree
 * as well as walking their children to be displayed.
 */
@Composable
fun SceneTreeNode(
	node: TreeValue<SceneItem>,
	collapsed: Boolean,
	nodeCollapsesChildren: Boolean,
	selectedId: Int,
	itemUi: ItemUi,
	toggleExpanded: (nodeId: Int) -> Unit,
	modifier: Modifier
) {
	AnimatedVisibility(visible = !collapsed, modifier = modifier) {
		val itemModifier = Modifier.alpha(if (node.value.id == selectedId) 0.5f else 1f)
		itemUi(
			node,
			toggleExpanded,
			nodeCollapsesChildren,
			itemModifier
		)
	}
}