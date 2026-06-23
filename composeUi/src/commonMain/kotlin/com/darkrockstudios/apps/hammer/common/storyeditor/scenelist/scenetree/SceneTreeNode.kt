package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

/**
 * Composable wrapper that handles rendering individual nodes in the tree.
 */
@Composable
fun SceneTreeNode(
	node: TreeValue<SceneItem>,
	nodeCollapsesChildren: Boolean,
	selectedId: Int,
	itemUi: ItemUi,
	toggleExpanded: (nodeId: Int) -> Unit,
	modifier: Modifier
) {
	Box(modifier) {
		val itemModifier = Modifier.alpha(if (node.value.id == selectedId) 0.5f else 1f)
		itemUi(
			node,
			toggleExpanded,
			nodeCollapsesChildren,
			itemModifier
		)
	}
}