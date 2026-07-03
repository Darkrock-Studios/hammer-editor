package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneTypeMeta
import com.darkrockstudios.apps.hammer.scene_delete_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_rename_dialog_label
import com.darkrockstudios.apps.hammer.scene_rename_dialog_rename_button
import com.darkrockstudios.apps.hammer.scene_rename_dialog_title

@Composable
internal fun SceneRenameDialog(
	scene: SceneItem,
	dismissDialog: (String?) -> Unit
) {
	var nameText by rememberSaveable { mutableStateOf(scene.name) }

	val validation = rememberNameValidation(nameText, NameKind.SceneItem)

	val meta = sceneTypeMeta(scene)

	fun submit() {
		if (validation.isValid) dismissDialog(nameText)
	}

	FormDialog(
		visible = true,
		marker = "§ RENAME",
		meta = meta,
		title = Res.string.scene_rename_dialog_title.get(),
		confirmLabel = Res.string.scene_rename_dialog_rename_button.get(),
		cancelLabel = Res.string.scene_delete_dialog_dismiss_button.get(),
		onConfirm = ::submit,
		onCancel = { dismissDialog(null) },
		onDismiss = { dismissDialog(null) },
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = nameText,
			onValueChange = { nameText = it },
			label = Res.string.scene_rename_dialog_label.get(),
			autoFocus = true,
			error = validation.fieldError(nameText),
			onImeAction = ::submit,
		)
	}
}
