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
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.delete_project_cancel
import com.darkrockstudios.apps.hammer.delete_project_confirm
import com.darkrockstudios.apps.hammer.delete_project_confirm_hint
import com.darkrockstudios.apps.hammer.delete_project_title
import com.darkrockstudios.apps.hammer.delete_project_warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProjectDeleteDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = close,
		visible = true,
		title = Res.string.delete_project_title.get(),
		modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)
	) {
		var confirmationText by rememberSaveable { mutableStateOf("") }

		val isConfirmed = confirmationText.trim().equals(projectDef.name, ignoreCase = true)

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.L)
				.padding(bottom = Ui.Padding.L)
		) {
			Text(
				text = Res.string.delete_project_warning.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.error
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			OutlinedTextField(
				value = confirmationText,
				onValueChange = { confirmationText = it },
				label = { Text(stringResource(Res.string.delete_project_confirm_hint, projectDef.name)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				keyboardOptions = KeyboardOptions(
					imeAction = ImeAction.Done
				),
				keyboardActions = KeyboardActions(
					onDone = {
						if (isConfirmed) {
							component.deleteProject(projectDef)
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
					Text(Res.string.delete_project_cancel.get())
				}

				Spacer(modifier = Modifier.width(Ui.Padding.M))

				Button(
					onClick = {
						component.deleteProject(projectDef)
						close()
					},
					enabled = isConfirmed,
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.error,
						contentColor = MaterialTheme.colorScheme.onError
					)
				) {
					Text(Res.string.delete_project_confirm.get())
				}
			}
		}
	}
}
