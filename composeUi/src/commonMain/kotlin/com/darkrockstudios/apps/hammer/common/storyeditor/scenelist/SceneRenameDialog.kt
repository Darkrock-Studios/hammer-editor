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
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneKindMeta
import com.darkrockstudios.apps.hammer.chapter_rename_dialog_label
import com.darkrockstudios.apps.hammer.chapter_rename_dialog_title
import com.darkrockstudios.apps.hammer.group_rename_dialog_label
import com.darkrockstudios.apps.hammer.group_rename_dialog_title
import com.darkrockstudios.apps.hammer.scene_delete_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_rename_dialog_label
import com.darkrockstudios.apps.hammer.scene_rename_dialog_rename_button
import com.darkrockstudios.apps.hammer.scene_rename_dialog_title

@Composable
internal fun SceneRenameDialog(
	scene: SceneItem,
	kind: SceneKind,
	dismissDialog: (String?) -> Unit
) {
	var nameText by rememberSaveable { mutableStateOf(scene.name) }

	val validation = rememberNameValidation(nameText, NameKind.SceneItem)

	val meta = sceneKindMeta(kind)
	val title = when (kind) {
		SceneKind.Chapter -> Res.string.chapter_rename_dialog_title
		SceneKind.Group -> Res.string.group_rename_dialog_title
		else -> Res.string.scene_rename_dialog_title
	}
	val label = when (kind) {
		SceneKind.Chapter -> Res.string.chapter_rename_dialog_label
		SceneKind.Group -> Res.string.group_rename_dialog_label
		else -> Res.string.scene_rename_dialog_label
	}

	fun submit() {
		if (validation.isValid) dismissDialog(nameText)
	}

	FormDialog(
		visible = true,
		marker = "§ RENAME",
		meta = meta,
		title = title.get(),
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
			label = label.get(),
			autoFocus = true,
			error = validation.fieldError(nameText),
			onImeAction = ::submit,
		)
	}
}
