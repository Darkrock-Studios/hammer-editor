package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@Composable
actual fun SceneGroupActionContainer(
	scene: SceneItem,
	shouldNux: Boolean,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	onCreateSceneClick: (scene: SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
	itemContent: @Composable (modifier: Modifier) -> Unit
) {
	val strRes = rememberStrRes()

	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_item_action_delete),
					onClick = { onSceneAltClick(scene) }
				),
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_item_action_rename),
					onClick = { onSceneRenameClick(scene) }
				),
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_group_action_create_scene),
					onClick = { onCreateSceneClick(scene) }
				),
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_group_action_create_group),
					onClick = { onCreateGroupClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}
