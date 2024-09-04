package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as DesktopPlatformSettingsComponent
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }
	var showDirectoryPicker by remember { mutableStateOf(false) }

	SpacerXL()

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			MR.strings.settings_projects_directory.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		TextField(
			value = projectsPathText,
			onValueChange = { projectsPathText = it },
			enabled = false,
			label = {
				Text(MR.strings.settings_projects_directory_hint.get())
			}
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		Button(onClick = { showDirectoryPicker = true }) {
			Text(MR.strings.settings_projects_directory_button.get())
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