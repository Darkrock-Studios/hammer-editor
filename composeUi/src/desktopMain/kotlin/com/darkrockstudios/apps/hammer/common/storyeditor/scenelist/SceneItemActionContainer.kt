package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.scene_list_item_action_delete
import com.darkrockstudios.apps.hammer.scene_list_item_action_rename

@Composable
actual fun SceneItemActionContainer(
	scene: SceneItem,
	onSceneDeleteClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	shouldNux: Boolean,
	itemContent: @Composable (modifier: Modifier) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()

	var deleteLabel by remember { mutableStateOf("") }
	var renameLabel by remember { mutableStateOf("") }

	LaunchedEffect(Unit) {
		deleteLabel = strRes.get(Res.string.scene_list_item_action_delete)
		renameLabel = strRes.get(Res.string.scene_list_item_action_rename)
	}

	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = deleteLabel,
					onClick = { onSceneDeleteClick(scene) }
				),
				ContextMenuItem(
					label = renameLabel,
					onClick = { onSceneRenameClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}