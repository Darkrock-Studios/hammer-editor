package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException

@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectsList, close: () -> Unit) {
	SimpleDialog(
		onCloseRequest = close,
		visible = show,
		title = Res.string.create_project_title.get(),
		modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)
	) {
		val state by component.state.subscribeAsState()
		val strRes = rememberStrRes()

		val projectName = state.createDialogProjectName

		val validationResult = remember(projectName) {
			ProjectsRepository.validateFileName(projectName.trim().ifEmpty { null })
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

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.L)
				.padding(bottom = Ui.Padding.L)
		) {
			OutlinedTextField(
				value = projectName,
				onValueChange = { component.onProjectNameUpdate(it) },
				label = { Text(Res.string.create_project_heading.get()) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				isError = projectName.isNotEmpty() && !isValid,
				supportingText = if (projectName.isNotEmpty() && !isValid) {
					{ Text(errorMessage ?: "") }
				} else null,
				keyboardOptions = KeyboardOptions(
					imeAction = ImeAction.Done
				),
				keyboardActions = KeyboardActions(
					onDone = {
						if (isValid) {
							component.createProject(projectName)
						}
					}
				)
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				TextButton(onClick = close) {
					Text(Res.string.create_project_cancel_button.get())
				}

				Spacer(modifier = Modifier.width(Ui.Padding.M))

				Button(
					onClick = { component.createProject(projectName) },
					enabled = isValid
				) {
					Text(Res.string.create_project_button.get())
				}
			}
		}
	}
}