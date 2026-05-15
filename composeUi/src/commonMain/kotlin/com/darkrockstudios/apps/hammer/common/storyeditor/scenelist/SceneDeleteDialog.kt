package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneDeleteDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	val strRes = rememberStrRes()

	var messageText by remember { mutableStateOf("") }
	LaunchedEffect(scene.name) {
		messageText = strRes.get(Res.string.scene_delete_dialog_message, scene.name)
	}

	ConfirmationDialog(
		visible = true,
		title = Res.string.scene_delete_dialog_title.get(),
		message = messageText,
		confirmLabel = Res.string.scene_delete_dialog_delete_button.get(),
		cancelLabel = Res.string.scene_delete_dialog_dismiss_button.get(),
		onConfirm = { dismissDialog(true) },
		onDismiss = { dismissDialog(false) },
		destructive = true,
		kind = "DELETE",
	)
}
