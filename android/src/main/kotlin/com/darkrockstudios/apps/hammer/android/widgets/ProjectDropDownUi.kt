package com.darkrockstudios.apps.hammer.android.widgets

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDropDownUi(projects: List<ProjectDef>, onProjectSelected: (ProjectDef) -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	var selectedOptionText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(
			TextFieldValue(
				text = projects.firstOrNull()?.name ?: ""
			)
		)
	}

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = {
			expanded = !expanded
		}
	) {
		OutlinedTextField(
			modifier = Modifier.menuAnchor(),
			readOnly = true,
			value = selectedOptionText,
			onValueChange = { selectedOptionText = it },
			label = { Text(stringResource(R.string.note_widget_dialog_projects_dropdown)) },
			trailingIcon = {
				ExposedDropdownMenuDefaults.TrailingIcon(
					expanded = expanded
				)
			},
			colors = ExposedDropdownMenuDefaults.textFieldColors()
		)
		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = {
				expanded = false
			}
		) {
			projects.forEach { selectionOption ->
				DropdownMenuItem(
					text = { Text(selectionOption.name) },
					onClick = {
						selectedOptionText = TextFieldValue(selectionOption.name)
						onProjectSelected(selectionOption)
						expanded = false
					}
				)
			}
		}
	}
}