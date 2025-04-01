package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@Composable
actual fun SceneItemActionContainer(
	scene: SceneItem,
	onSceneDeleteClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	shouldNux: Boolean,
	itemContent: @Composable (modifier: Modifier) -> Unit,
) {
	val strRes = rememberStrRes()

	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_item_action_delete),
					onClick = { onSceneDeleteClick(scene) }
				),
				ContextMenuItem(
					label = strRes.get(MR.strings.scene_list_item_action_rename),
					onClick = { onSceneRenameClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}