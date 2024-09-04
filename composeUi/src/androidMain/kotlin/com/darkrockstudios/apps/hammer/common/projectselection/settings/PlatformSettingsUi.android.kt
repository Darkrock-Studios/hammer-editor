package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AndroidPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import dev.icerock.moko.permissions.compose.BindEffect

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as AndroidPlatformSettingsComponent
	val state by component.state.subscribeAsState()

	val projectsPathText by remember { derivedStateOf { state.projectsDir.path } }
	//var showDirectoryPicker by remember { mutableStateOf(false) }

	BindEffect(component.permissionsController)

	Text(
		MR.strings.settings_platform_settings_title.get(),
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.onBackground,
	)
	Row {
		Checkbox(checked = state.keepScreenOn, onCheckedChange = component::updateKeepScreenOn)
		Text(
			MR.strings.settings_keep_screen_on.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground,
			modifier = Modifier.align(Alignment.CenterVertically),
		)
	}

	SpacerL()

	Text(
		"Project data storage",
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.onBackground,
	)

	if (state.dataStorageInternal.not()) {
		Text(
			projectsPathText,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
		)
	} else {
		Text(
			"Using Internal data storage",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
		)
	}

	if (state.fileAccessGranted.not()) {
		Button(onClick = component::promptForFileAccess) {
			Text("Grant access")
		}
	} else {
		if (state.dataStorageInternal) {
			Button(onClick = component::setExternalStorage) {
				Text("Switch to External Storage")
			}
		} else {
			Button(onClick = component::setInternalStorage) {
				Text("Switch to Internal Storage")
			}
		}
	}

	// TODO this isn't working yet, probably need a different directory picker
//	DirectoryPicker(showDirectoryPicker) { path ->
//		showDirectoryPicker = false
//
//		if (path != null) {
//			projectsPathText = path
//			component.setProjectsDir(projectsPathText)
//		}
//	}
}