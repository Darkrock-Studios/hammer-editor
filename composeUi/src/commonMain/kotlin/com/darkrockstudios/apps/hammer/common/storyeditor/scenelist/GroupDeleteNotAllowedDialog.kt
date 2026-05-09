package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.group_cannot_delete_dialog_message
import com.darkrockstudios.apps.hammer.group_cannot_delete_dialog_title
import com.darkrockstudios.apps.hammer.scene_delete_dialog_dismiss_button

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun GroupDeleteNotAllowedDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	ConfirmationDialog(
		visible = true,
		title = Res.string.group_cannot_delete_dialog_title.get(),
		message = Res.string.group_cannot_delete_dialog_message.get(scene.name),
		confirmLabel = Res.string.scene_delete_dialog_dismiss_button.get(),
		onConfirm = { dismissDialog(false) },
		onDismiss = { dismissDialog(false) },
		kind = "BLOCKED",
	)
}
