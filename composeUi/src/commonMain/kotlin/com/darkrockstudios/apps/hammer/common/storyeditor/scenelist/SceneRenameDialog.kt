package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.Res
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_name_label
import com.darkrockstudios.apps.hammer.chapter_rename_dialog_title
import com.darkrockstudios.apps.hammer.group_rename_dialog_title
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

	fun submit() {
		if (validation.isValid) dismissDialog(nameText)
	}

	AnimatedDialog(
		visible = true,
		onCloseRequest = { dismissDialog(null) },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		SceneRenameCard(
			kind = kind,
			name = nameText,
			onNameChange = { nameText = it },
			error = validation.fieldError(nameText),
			confirmEnabled = validation.isValid,
			onConfirm = ::submit,
			onCancel = { dismissDialog(null) },
			autoFocus = true,
		)
	}
}

@Composable
internal fun SceneRenameCard(
	kind: SceneKind,
	name: String,
	onNameChange: (String) -> Unit,
	error: String?,
	confirmEnabled: Boolean,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	autoFocus: Boolean = false,
	fieldTestTag: String? = null,
) {
	val title = when (kind) {
		SceneKind.Chapter -> Res.string.chapter_rename_dialog_title
		SceneKind.Group -> Res.string.group_rename_dialog_title
		else -> Res.string.scene_rename_dialog_title
	}

	FormDialogScaffold(
		title = title.get(),
		confirmLabel = Res.string.scene_rename_dialog_rename_button.get(),
		cancelLabel = Res.string.create_sceneitem_dialog_dismiss_button.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		confirmEnabled = confirmEnabled,
	) {
		FormField(
			value = name,
			onValueChange = onNameChange,
			label = Res.string.create_sceneitem_dialog_name_label.get(),
			autoFocus = autoFocus,
			error = error,
			onImeAction = onConfirm,
			testTag = fieldTestTag,
		)
	}
}
