package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
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
	val strRes = rememberStrRes()

	fun close(text: String?) {
		onClose(text)
		nameText = ""
	}

	val validationResult = remember(nameText) {
		ProjectsRepository.validateFileName(nameText.trim().ifEmpty { null })
	}
	val isValid = validationResult.isSuccess

	val errorMessage by produceState<String?>(null, validationResult) {
		value = if (isFailure(validationResult)) {
			when (val exception = validationResult.exception) {
				is ValidationFailedException -> strRes.get(exception.errorMessage)
				else -> validationResult.displayMessage?.text(strRes)
			}
		} else null
	}

	fun submit() {
		if (isValid) close(nameText)
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
		confirmEnabled = isValid,
	) {
		FormField(
			value = nameText,
			onValueChange = { nameText = it },
			label = textLabel,
			autoFocus = true,
			error = if (nameText.isNotEmpty() && !isValid) errorMessage else null,
			onImeAction = ::submit,
		)
	}
}
