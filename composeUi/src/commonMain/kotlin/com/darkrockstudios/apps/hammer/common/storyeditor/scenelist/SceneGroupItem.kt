package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdIndentStep
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentFor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentRails
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.scene_group_item_collapsed
import com.darkrockstudios.apps.hammer.scene_group_item_expanded
import com.darkrockstudios.apps.hammer.scene_group_scene_count_format

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
	onSceneMoveRequest: (SceneItem) -> Unit,
	onCreateSceneClick: (SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode
	val isTopLevel = sceneNode.depth <= 1
	val indent = hdIndentFor(sceneNode.depth)
	val railColor = MaterialTheme.colorScheme.outlineVariant

	val groupModifier = draggable
		.fillMaxWidth()
		.testTag(sceneGroupTag(scene.id))
		.hdIndentRails(levels = sceneNode.depth - 1, color = railColor)
		.clickable { toggleExpand(scene.id) }

	val sceneCount = sceneNode.count { it.value.type == SceneItem.Type.Scene }

	SceneGroupActionContainer(
		scene = scene,
		shouldNux = shouldNux,
		onSceneAltClick = onSceneDeleteRequest,
		onSceneRenameClick = onSceneRenameRequest,
		onSceneMoveClick = onSceneMoveRequest,
		onCreateSceneClick = onCreateSceneClick,
		onCreateGroupClick = onCreateGroupClick,
	) {
		Box(modifier = groupModifier) {
			Column(modifier = Modifier.fillMaxWidth()) {
				// A chapter break cuts the full panel; a nested rule hangs at the group's own indent.
				HorizontalDivider(
					modifier = Modifier.padding(start = if (isTopLevel) 0.dp else indent),
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = indent,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.L,
						),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Icon(
						imageVector = if (collapsed) {
							Icons.AutoMirrored.Filled.KeyboardArrowRight
						} else {
							Icons.Filled.KeyboardArrowDown
						},
						contentDescription = if (collapsed) {
							Res.string.scene_group_item_collapsed.get()
						} else {
							Res.string.scene_group_item_expanded.get()
						},
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(HdIndentStep),
					)

					Text(
						text = scene.name,
						style = if (isTopLevel) {
							MaterialTheme.typography.titleSmall
						} else {
							MaterialTheme.typography.bodyMedium
						},
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.weight(1f),
					)

					if (sceneCount > 0) {
						HdMonoLabel(
							text = Res.string.scene_group_scene_count_format.get(sceneCount),
							modifier = Modifier.padding(start = Ui.Padding.M),
						)
					}
				}
			}

			val groupHasDirtyBuffer = children.any { hasDirtyBuffer.contains(it.value.id) }
			Unsaved(groupHasDirtyBuffer)
		}
	}
}
