package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.scene_group_item_collapsed
import com.darkrockstudios.apps.hammer.scene_group_item_expanded

fun sceneGroupTag(id: Int) = "scene-group-$id"

@Composable
internal fun SceneGroupItem(
	sceneNode: TreeValue<SceneItem>,
	draggable: Modifier,
	hasDirtyBuffer: Set<Int>,
	toggleExpand: (nodeId: Int) -> Unit,
	collapsed: Boolean,
	shouldNux: Boolean,
	onSceneDeleteRequest: (SceneItem) -> Unit,
	onSceneRenameRequest: (SceneItem) -> Unit,
	onCreateSceneClick: (SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode
	val isTopLevel = sceneNode.depth == 1

	val groupModifier = draggable
		.fillMaxWidth()
		.testTag(sceneGroupTag(scene.id))
		.clickable { toggleExpand(scene.id) }

	SceneGroupActionContainer(
		scene = scene,
		shouldNux = shouldNux,
		onSceneAltClick = onSceneDeleteRequest,
		onSceneRenameClick = onSceneRenameRequest,
		onCreateSceneClick = onCreateSceneClick,
		onCreateGroupClick = onCreateGroupClick,
	) {
		Box(modifier = groupModifier) {
			Column(modifier = Modifier.fillMaxWidth()) {
				if (isTopLevel) {
					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
				}

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = if (isTopLevel) {
								Ui.Padding.XL
							} else {
								Ui.Padding.XL + (Ui.Padding.XL * (sceneNode.depth - 2).coerceAtLeast(0))
							},
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.L,
						),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (isTopLevel) {
						Icon(
							imageVector = if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
							contentDescription = if (collapsed) {
								Res.string.scene_group_item_collapsed.get()
							} else {
								Res.string.scene_group_item_expanded.get()
							},
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(16.dp).padding(end = Ui.Padding.S),
						)
						Text(
							text = scene.name,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.weight(1f),
						)
					} else {
						Icon(
							imageVector = if (collapsed) Icons.Filled.Folder else Icons.Filled.FolderOpen,
							contentDescription = if (collapsed) {
								Res.string.scene_group_item_collapsed.get()
							} else {
								Res.string.scene_group_item_expanded.get()
							},
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(20.dp).padding(end = Ui.Padding.M),
						)
						Text(
							text = scene.name,
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.weight(1f),
						)
					}
				}
			}

			val groupHasDirtyBuffer = children.any { hasDirtyBuffer.contains(it.value.id) }
			Unsaved(groupHasDirtyBuffer)
		}
	}
}
