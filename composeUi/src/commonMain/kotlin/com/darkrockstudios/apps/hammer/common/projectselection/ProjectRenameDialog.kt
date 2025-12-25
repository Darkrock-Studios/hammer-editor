package com.darkrockstudios.apps.hammer.common.projectselection

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
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.rename_project_button
import com.darkrockstudios.apps.hammer.rename_project_cancel_button
import com.darkrockstudios.apps.hammer.rename_project_heading
import com.darkrockstudios.apps.hammer.rename_project_title

@Composable
fun ProjectRenameDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = close,
		visible = true,
		title = Res.string.rename_project_title.get(),
		modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)
	) {
		val strRes = rememberStrRes()
		var projectName by rememberSaveable { mutableStateOf(projectDef.name) }

		val validationResult = remember(projectName) {
			ProjectsRepository.validateFileName(projectName.trim().ifEmpty { null })
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
				value = projectName,
				onValueChange = { projectName = it },
				label = { Text(Res.string.rename_project_heading.get()) },
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
							component.renameProject(projectDef, projectName)
							close()
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
					Text(Res.string.rename_project_cancel_button.get())
				}

				Spacer(modifier = Modifier.width(Ui.Padding.M))

				Button(
					onClick = {
						component.renameProject(projectDef, projectName)
						close()
					},
					enabled = isValid
				) {
					Text(Res.string.rename_project_button.get())
				}
			}
		}
	}
}