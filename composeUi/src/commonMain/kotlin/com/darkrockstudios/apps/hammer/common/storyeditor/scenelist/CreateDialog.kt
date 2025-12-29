package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_create_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button

@OptIn(ExperimentalMaterial3Api::class)
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

	SimpleDialog(
		visible = show,
		title = title,
		onCloseRequest = { close(null) },
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
				label = { Text(textLabel) },
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
							close(nameText)
						}
					}
				)
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				TextButton(onClick = { close(null) }) {
					Text(Res.string.create_sceneitem_dialog_dismiss_button.get())
				}

				Spacer(modifier = Modifier.width(Ui.Padding.M))

				Button(
					onClick = { close(nameText) },
					enabled = isValid
				) {
					Text(Res.string.create_sceneitem_dialog_create_button.get())
				}
			}
		}
	}
}
