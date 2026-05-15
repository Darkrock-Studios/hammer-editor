package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException

@Composable
internal fun SceneRenameDialog(
	scene: SceneItem,
	dismissDialog: (String?) -> Unit
) {
	var nameText by rememberSaveable { mutableStateOf(scene.name) }
	val strRes = rememberStrRes()

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

	val meta = when (scene.type) {
		SceneItem.Type.Scene -> "SCENE"
		SceneItem.Type.Group -> "GROUP"
		SceneItem.Type.Root -> "ROOT"
	}

	fun submit() {
		if (isValid) dismissDialog(nameText)
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
		confirmEnabled = isValid,
	) {
		FormField(
			value = nameText,
			onValueChange = { nameText = it },
			label = Res.string.scene_rename_dialog_label.get(),
			autoFocus = true,
			error = if (nameText.isNotEmpty() && !isValid) errorMessage else null,
			onImeAction = ::submit,
		)
	}
}
