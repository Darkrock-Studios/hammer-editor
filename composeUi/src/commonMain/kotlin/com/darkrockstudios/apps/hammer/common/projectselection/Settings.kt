package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.globalsettings.UiTheme
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Settings(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }
	var showDirectoryPicker by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.padding(Ui.Padding.L)
			.fillMaxSize()
	) {
		Text(
			"\u2699 Settings",
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onBackground,
			modifier = Modifier.padding(Ui.Padding.L)
		)

		Divider(modifier = Modifier.fillMaxWidth())

		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Column(modifier = Modifier.padding(Ui.Padding.M)) {
			Text(
				"UI Theme",
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onBackground,
			)

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			val themeOptions = remember { UiTheme.values().toList() }
			ExposedDropDown(
				modifier = Modifier.defaultMinSize(minWidth = 256.dp),
				items = themeOptions,
				defaultIndex = themeOptions.indexOf(state.uiTheme)
			) { selectedTheme ->
				if (selectedTheme != null) {
					component.setUiTheme(selectedTheme)
				}
			}
		}

		if (component.showProjectDirectory) {
			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Column(modifier = Modifier.padding(Ui.Padding.M)) {
				Text(
					"Projects Directory",
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onBackground,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				TextField(
					value = projectsPathText,
					onValueChange = { projectsPathText = it },
					enabled = false,
					label = { Text("Path to Projects Directory") }
				)

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				Button(onClick = { showDirectoryPicker = true }) {
					Text("Select Dir")
				}
			}
		}
	}

	DirectoryPicker(showDirectoryPicker) { path ->
		showDirectoryPicker = false

		if (path != null) {
			projectsPathText = path
			component.setProjectsDir(projectsPathText)
		}
	}
}