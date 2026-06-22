package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_create_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button

@Composable
internal fun CreateDialog(
	show: Boolean,
	title: String,
	textLabel: String,
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

	FormDialog(
		visible = show,
		marker = "§ NEW",
		title = title,
		confirmLabel = Res.string.create_sceneitem_dialog_create_button.get(),
		cancelLabel = Res.string.create_sceneitem_dialog_dismiss_button.get(),
		onConfirm = ::submit,
		onCancel = { close(null) },
		onDismiss = { close(null) },
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = nameText,
			onValueChange = { nameText = it },
			label = textLabel,
			autoFocus = true,
			error = validation.fieldError(nameText),
			onImeAction = ::submit,
			testTag = CREATE_ITEM_NAME_FIELD_TAG,
		)
	}
}
