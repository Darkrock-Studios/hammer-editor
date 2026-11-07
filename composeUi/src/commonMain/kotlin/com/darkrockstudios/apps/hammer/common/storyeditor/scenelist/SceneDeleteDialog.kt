package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneDeleteDialog(scene: SceneItem, kind: SceneKind, dismissDialog: (Boolean) -> Unit) {
	val strRes = rememberStrRes()

	val title = when (kind) {
		SceneKind.Chapter -> Res.string.chapter_delete_dialog_title
		SceneKind.Group -> Res.string.group_delete_dialog_title
		else -> Res.string.scene_delete_dialog_title
	}
	val message = when (kind) {
		SceneKind.Chapter -> Res.string.chapter_delete_dialog_message
		SceneKind.Group -> Res.string.group_delete_dialog_message
		else -> Res.string.scene_delete_dialog_message
	}

	var messageText by remember { mutableStateOf("") }
	LaunchedEffect(scene.name, kind) {
		messageText = strRes.get(message, scene.name)
	}

	ConfirmationDialog(
		visible = true,
		title = title.get(),
		message = messageText,
		confirmLabel = Res.string.scene_delete_dialog_delete_button.get(),
		cancelLabel = Res.string.scene_delete_dialog_dismiss_button.get(),
		onConfirm = { dismissDialog(true) },
		onDismiss = { dismissDialog(false) },
		destructive = true,
		kind = "DELETE",
	)
}
