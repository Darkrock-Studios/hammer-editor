package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as DesktopPlatformSettings
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }

	val directoryPickerLauncher = rememberDirectoryPickerLauncher { directory ->
		if (directory != null) {
			projectsPathText = directory.absolutePath()
			component.setProjectsDir(projectsPathText)
		}
	}

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			Res.string.settings_projects_directory.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			Res.string.settings_projects_directory_description.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
			fontStyle = FontStyle.Italic
		)

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		TextField(
			value = projectsPathText,
			onValueChange = { projectsPathText = it },
			enabled = false,
			label = {
				Text(Res.string.settings_projects_directory_hint.get())
			}
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		Button(onClick = { directoryPickerLauncher.launch() }) {
			Text(Res.string.settings_projects_directory_button.get())
		}
	}
}