package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_projects_directory
import com.darkrockstudios.apps.hammer.settings_projects_directory_button
import com.darkrockstudios.apps.hammer.settings_projects_directory_description
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.launch

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as DesktopPlatformSettings
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }

	val scope = rememberCoroutineScope()

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(
			text = Res.string.settings_projects_directory_description.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMonoLabel(text = Res.string.settings_projects_directory.get())
			Text(
				text = projectsPathText,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		HdHairlineButton(
			label = Res.string.settings_projects_directory_button.get(),
			onClick = {
				scope.launch {
					val directory = FileKit.openDirectoryPicker()
					if (directory != null) {
						projectsPathText = directory.absolutePath()
						component.setProjectsDir(projectsPathText)
					}
				}
			},
		)
	}
}
