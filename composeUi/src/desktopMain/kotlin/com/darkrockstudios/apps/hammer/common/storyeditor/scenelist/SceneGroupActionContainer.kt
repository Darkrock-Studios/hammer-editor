package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.*
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
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()

	var deleteLabel by remember { mutableStateOf("") }
	var renameLabel by remember { mutableStateOf("") }
	var createSceneLabel by remember { mutableStateOf("") }
	var createGroupLabel by remember { mutableStateOf("") }

	LaunchedEffect(Unit) {
		deleteLabel = strRes.get(Res.string.scene_list_item_action_delete)
		renameLabel = strRes.get(Res.string.scene_list_item_action_rename)
		createSceneLabel = strRes.get(Res.string.scene_list_group_action_create_scene)
		createGroupLabel = strRes.get(Res.string.scene_list_group_action_create_group)
	}

	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = deleteLabel,
					onClick = { onSceneAltClick(scene) }
				),
				ContextMenuItem(
					label = renameLabel,
					onClick = { onSceneRenameClick(scene) }
				),
				ContextMenuItem(
					label = createSceneLabel,
					onClick = { onCreateSceneClick(scene) }
				),
				ContextMenuItem(
					label = createGroupLabel,
					onClick = { onCreateGroupClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}
