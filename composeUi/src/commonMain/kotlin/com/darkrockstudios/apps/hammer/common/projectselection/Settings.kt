package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.globalsettings.UiTheme
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Settings(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }
	var showDirectoryPicker by remember { mutableStateOf(false) }
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

	Box(modifier = modifier.fillMaxSize()) {
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

			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
					Spacer(modifier = Modifier.size(Ui.Padding.XL))

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

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Column(modifier = Modifier.padding(Ui.Padding.M)) {
					Text(
						"Example Project",
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground,
					)
					Text(
						"Install an example project to see how different features are used.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onBackground,
						fontStyle = FontStyle.Italic
					)

					Spacer(modifier = Modifier.size(Ui.Padding.M))

					Button(onClick = {
						scope.launch {
							component.reinstallExampleProject()
							snackbarHostState.showSnackbar("Example project created.")
						}
					}) {
						Text("Reinstall")
					}
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}

	DirectoryPicker(showDirectoryPicker) { path ->
		showDirectoryPicker = false

		if (path != null) {
			projectsPathText = path
			component.setProjectsDir(projectsPathText)
		}
	}
}