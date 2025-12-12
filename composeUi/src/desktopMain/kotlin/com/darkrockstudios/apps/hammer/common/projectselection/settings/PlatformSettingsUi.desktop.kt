package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_projects_directory
import com.darkrockstudios.apps.hammer.settings_projects_directory_button
import com.darkrockstudios.apps.hammer.settings_projects_directory_hint
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

	SpacerXL()

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			Res.string.settings_projects_directory.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

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