package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.scene_list_item_action_archive
import com.darkrockstudios.apps.hammer.scene_list_item_action_delete
import com.darkrockstudios.apps.hammer.scene_list_item_action_move
import com.darkrockstudios.apps.hammer.scene_list_item_action_rename

@Composable
actual fun SceneItemActionContainer(
	scene: SceneItem,
	onSceneDeleteClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	onSceneArchiveClick: (scene: SceneItem) -> Unit,
	onSceneMoveClick: (scene: SceneItem) -> Unit,
	shouldNux: Boolean,
	itemContent: @Composable (modifier: Modifier) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()

	var deleteLabel by remember { mutableStateOf("") }
	var renameLabel by remember { mutableStateOf("") }
	var archiveLabel by remember { mutableStateOf("") }
	var moveLabel by remember { mutableStateOf("") }

	LaunchedEffect(Unit) {
		deleteLabel = strRes.get(Res.string.scene_list_item_action_delete)
		renameLabel = strRes.get(Res.string.scene_list_item_action_rename)
		archiveLabel = strRes.get(Res.string.scene_list_item_action_archive)
		moveLabel = strRes.get(Res.string.scene_list_item_action_move)
	}

	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = archiveLabel,
					onClick = { onSceneArchiveClick(scene) }
				),
				ContextMenuItem(
					label = renameLabel,
					onClick = { onSceneRenameClick(scene) }
				),
				ContextMenuItem(
					label = moveLabel,
					onClick = { onSceneMoveClick(scene) }
				),
				ContextMenuItem(
					label = deleteLabel,
					onClick = { onSceneDeleteClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}