package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
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

	SimpleDialog(
		onCloseRequest = { dismissDialog(null) },
		visible = true,
		title = Res.string.scene_rename_dialog_title.get(),
		modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)
	) {
		val strRes = rememberStrRes()

		val validationResult = remember(nameText) {
			ProjectsRepository.validateFileName(nameText.trim().ifEmpty { null })
		}

		val isValid = validationResult.isSuccess

		val errorMessage by produceState<String?>(null, validationResult) {
			value = if (isFailure(validationResult)) {
				when (val exception = validationResult.exception) {
					is ValidationFailedException -> strRes.get(exception.errorMessage)
					else -> validationResult.displayMessage?.let { strRes.get(it.r, it.args) }
				}
			} else null
		}

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.L)
				.padding(bottom = Ui.Padding.L)
		) {
			OutlinedTextField(
				value = nameText,
				onValueChange = { nameText = it },
				label = { Text(Res.string.scene_rename_dialog_label.get()) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				isError = nameText.isNotEmpty() && !isValid,
				supportingText = if (nameText.isNotEmpty() && !isValid) {
					{ Text(errorMessage ?: "") }
				} else null,
				keyboardOptions = KeyboardOptions(
					imeAction = ImeAction.Done
				),
				keyboardActions = KeyboardActions(
					onDone = {
						if (isValid) {
							dismissDialog(nameText)
						}
					}
				)
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				TextButton(onClick = { dismissDialog(null) }) {
					Text(Res.string.scene_delete_dialog_dismiss_button.get())
				}

				Spacer(modifier = Modifier.width(Ui.Padding.M))

				Button(
					onClick = { dismissDialog(nameText) },
					enabled = isValid
				) {
					Text(Res.string.scene_rename_dialog_rename_button.get())
				}
			}
		}
	}
}
