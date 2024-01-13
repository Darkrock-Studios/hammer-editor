package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.bottomBorder
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

@Composable
internal fun SceneGroupItem(
	sceneNode: TreeValue<SceneItem>,
	draggable: Modifier,
	hasDirtyBuffer: Set<Int>,
	toggleExpand: (nodeId: Int) -> Unit,
	collapsed: Boolean,
	onSceneAltClick: (SceneItem) -> Unit,
	onCreateSceneClick: (SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode

	var groupModifier = draggable
		.fillMaxWidth()
		.padding(start = (Ui.Padding.L * (sceneNode.depth - 1) * 2).coerceAtLeast(0.dp))
		.clickable(onClick = { toggleExpand(sceneNode.value.id) })

	if (!collapsed) {
		groupModifier = groupModifier.bottomBorder(1.dp, MaterialTheme.colorScheme.outline)
	}

	SceneGroupActionContainer(
		scene = scene,
		onSceneAltClick = onSceneAltClick,
		onCreateSceneClick = onCreateSceneClick,
		onCreateGroupClick = onCreateGroupClick,
	) {
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
							contentDescription = MR.strings.scene_group_item_collapsed.get(),
							modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
						)
					} else {
						Icon(
							imageVector = Icons.Filled.FolderOpen,
							contentDescription = MR.strings.scene_group_item_expanded.get(),
							modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
						)
					}

					Text(
						scene.name,
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.bodyLarge
					)
				}

				val hasDirtyBuffer = children.any { hasDirtyBuffer.contains(it.value.id) }
				Unsaved(hasDirtyBuffer)
			}
		}
	}
}
