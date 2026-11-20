package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun DiscardBufferDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	ConfirmationDialog(
		visible = true,
		title = Res.string.discard_buffer_dialog_title.get(scene.name),
		message = Res.string.discard_buffer_dialog_message.get(scene.name),
		confirmLabel = Res.string.discard_buffer_dialog_confirm.get(),
		cancelLabel = Res.string.discard_buffer_dialog_cancel.get(),
		onConfirm = { dismissDialog(true) },
		onDismiss = { dismissDialog(false) },
		destructive = true,
		kind = Res.string.confirm_kind_discard.get(),
	)
}
