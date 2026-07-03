package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.scene_list_group_action_create_group
import com.darkrockstudios.apps.hammer.scene_list_group_action_create_scene
import com.darkrockstudios.apps.hammer.scene_list_item_action_delete
import com.darkrockstudios.apps.hammer.scene_list_item_action_move
import com.darkrockstudios.apps.hammer.scene_list_item_action_rename

@Composable
actual fun SceneGroupActionContainer(
	scene: SceneItem,
	shouldNux: Boolean,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	onSceneMoveClick: (scene: SceneItem) -> Unit,
	onCreateSceneClick: (scene: SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
	itemContent: @Composable (modifier: Modifier) -> Unit
) {
	LongPressMenuContainer(
		entries = listOf(
			LongPressMenuEntry(
				label = Res.string.scene_list_item_action_delete.get(),
				icon = Icons.Outlined.Delete,
				onClick = { onSceneAltClick(scene) },
			),
			LongPressMenuEntry(
				label = Res.string.scene_list_item_action_rename.get(),
				icon = Icons.Outlined.Edit,
				onClick = { onSceneRenameClick(scene) },
			),
			LongPressMenuEntry(
				label = Res.string.scene_list_item_action_move.get(),
				icon = Icons.AutoMirrored.Outlined.DriveFileMove,
				onClick = { onSceneMoveClick(scene) },
			),
			LongPressMenuEntry(
				label = Res.string.scene_list_group_action_create_scene.get(),
				icon = Icons.Outlined.Add,
				onClick = { onCreateSceneClick(scene) },
			),
			LongPressMenuEntry(
				label = Res.string.scene_list_group_action_create_group.get(),
				icon = Icons.Outlined.Folder,
				onClick = { onCreateGroupClick(scene) },
			),
		),
		itemContent = itemContent,
	)
}
