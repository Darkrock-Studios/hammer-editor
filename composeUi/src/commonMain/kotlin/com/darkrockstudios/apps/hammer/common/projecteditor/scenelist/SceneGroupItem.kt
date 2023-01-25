package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.bottomBorder
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

@Composable
internal fun SceneGroupItem(
	sceneNode: TreeValue<SceneItem>,
	draggable: Modifier,
	hasDirtyBuffer: Set<Int>,
	toggleExpand: (nodeId: Int) -> Unit,
	collapsed: Boolean,
	onSceneAltClick: (SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode

	var groupModifier = draggable
		.fillMaxWidth()
		.padding(start = (Ui.Padding.L * (sceneNode.depth - 1) * 2).coerceAtLeast(0.dp))
		.clickable(onClick = { toggleExpand(sceneNode.value.id) })

	if (!collapsed) {
		groupModifier = groupModifier.bottomBorder(1.dp, MaterialTheme.colorScheme.outline)
	}

	Surface(
		modifier = groupModifier,
		tonalElevation = if (collapsed) 1.dp else 0.dp,
	) {
		BoxWithConstraints {
			Row(
				modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				if (collapsed) {
					Icon(
						imageVector = Icons.Filled.Folder,
						contentDescription = "Group Collapsed",
						modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
					)
				} else {
					Icon(
						imageVector = Icons.Filled.FolderOpen,
						contentDescription = "Group Expanded",
						modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
					)
				}

				Text(
					scene.name,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.bodyLarge
				)

				if (children.isEmpty()) {
					IconButton(
						onClick = { onSceneAltClick(scene) },
						modifier = Modifier.size(Ui.MIN_TOUCH_SIZE)
					) {
						Icon(Icons.Filled.Delete, contentDescription = "Delete Group")
					}
				} else {
					Spacer(modifier = Modifier.size(Ui.MIN_TOUCH_SIZE))
				}
			}

			val hasDirtyBuffer = children.any { hasDirtyBuffer.contains(it.value.id) }
			Unsaved(hasDirtyBuffer)
		}
	}
}