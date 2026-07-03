package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
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
	LongPressMenuContainer(
		entries = listOf(
			LongPressMenuEntry(
				label = Res.string.scene_list_item_action_archive.get(),
				icon = Icons.Outlined.Archive,
				onClick = { onSceneArchiveClick(scene) },
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
				label = Res.string.scene_list_item_action_delete.get(),
				icon = Icons.Outlined.Delete,
				onClick = { onSceneDeleteClick(scene) },
			),
		),
		itemContent = itemContent,
	)
}
