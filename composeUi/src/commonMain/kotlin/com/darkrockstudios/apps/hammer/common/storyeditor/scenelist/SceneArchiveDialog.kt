package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneArchiveDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	ConfirmationDialog(
		visible = true,
		title = Res.string.scene_archive_dialog_title.get(scene.name),
		message = Res.string.scene_archive_dialog_message.get(scene.name),
		confirmLabel = Res.string.scene_archive_dialog_archive_button.get(),
		cancelLabel = Res.string.scene_archive_dialog_dismiss_button.get(),
		onConfirm = { dismissDialog(true) },
		onDismiss = { dismissDialog(false) },
		kind = Res.string.confirm_kind_archive.get(),
	)
}
