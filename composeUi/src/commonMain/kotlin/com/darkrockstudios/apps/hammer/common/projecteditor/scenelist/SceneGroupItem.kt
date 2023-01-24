package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.bottomBorder
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

@OptIn(ExperimentalMaterial3Api::class)
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

	var modifier = draggable
		.fillMaxWidth()
		.padding(start = (Ui.Padding.XL + (Ui.Padding.XL * (sceneNode.depth - 1) * 2)).coerceAtLeast(0.dp))
		.clickable(onClick = { toggleExpand(sceneNode.value.id) })

	if (!collapsed) {
		modifier = modifier.bottomBorder(1.dp, MaterialTheme.colorScheme.outline)
	}

	Surface(
		modifier = modifier,
		tonalElevation = if (collapsed) 1.dp else 0.dp,
	) {
		BoxWithConstraints {
			Row(
				modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth(),
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
					IconButton(onClick = { onSceneAltClick(scene) }, modifier = Modifier) {
						Icon(
							imageVector = Icons.Filled.Delete,
							contentDescription = "Delete",
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}

			val hasDirtyBuffer = children.any { hasDirtyBuffer.contains(it.value.id) }
			Unsaved(hasDirtyBuffer)
		}
	}
}