package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_create_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_name_label
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_parent

@Composable
internal fun CreateDialog(
	show: Boolean,
	title: String,
	/** The group the new item goes into; null at the top level. */
	parentName: String?,
	onClose: (name: String?) -> Unit
) {
	var nameText by rememberSaveable { mutableStateOf("") }

	fun close(text: String?) {
		onClose(text)
		nameText = ""
	}

	val validation = rememberNameValidation(nameText, NameKind.SceneItem)

	fun submit() {
		if (validation.isValid) close(nameText)
	}

	AnimatedDialog(
		visible = show,
		onCloseRequest = { close(null) },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		CreateDialogCard(
			title = title,
			parentName = parentName,
			name = nameText,
			onNameChange = { nameText = it },
			error = validation.fieldError(nameText),
			confirmEnabled = validation.isValid,
			onConfirm = ::submit,
			onCancel = { close(null) },
			autoFocus = true,
		)
	}
}

@Composable
internal fun CreateDialogCard(
	title: String,
	parentName: String?,
	name: String,
	onNameChange: (String) -> Unit,
	error: String?,
	confirmEnabled: Boolean,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	autoFocus: Boolean = false,
) {
	FormDialogScaffold(
		title = title,
		confirmLabel = Res.string.create_sceneitem_dialog_create_button.get(),
		cancelLabel = Res.string.create_sceneitem_dialog_dismiss_button.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		meta = parentName?.let { Res.string.create_sceneitem_dialog_parent.get(it) },
		confirmEnabled = confirmEnabled,
	) {
		FormField(
			value = name,
			onValueChange = onNameChange,
			label = Res.string.create_sceneitem_dialog_name_label.get(),
			autoFocus = autoFocus,
			error = error,
			onImeAction = onConfirm,
			testTag = CREATE_ITEM_NAME_FIELD_TAG,
		)
	}
}
