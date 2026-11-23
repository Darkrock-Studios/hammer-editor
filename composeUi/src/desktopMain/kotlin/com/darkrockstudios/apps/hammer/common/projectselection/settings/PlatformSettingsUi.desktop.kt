package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.settings_row_projects_folder
import com.darkrockstudios.apps.hammer.settings_row_projects_folder_button
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_automatic_update_checks
import com.darkrockstudios.apps.hammer.settings_automatic_update_checks_hint
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

	HdSettingRow(
		title = Res.string.settings_row_projects_folder.get(),
		meta = projectsPathText,
	) {
		HdHairlineButton(
			label = Res.string.settings_row_projects_folder_button.get(),
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
	if (state.updateChecksSupported) {
		HdHairlineToggleRow(
			checked = state.automaticUpdateChecks,
			onCheckedChange = component::setAutomaticUpdateChecks,
			label = Res.string.settings_automatic_update_checks.get(),
			hint = Res.string.settings_automatic_update_checks_hint.get(),
		)
	}
	CommandLineSettings()
}
