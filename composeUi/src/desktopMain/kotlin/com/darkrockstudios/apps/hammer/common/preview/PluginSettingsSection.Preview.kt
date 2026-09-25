package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.PluginSettingsSection
import com.darkrockstudios.apps.hammer.settings_plugins_header

private val previewPane = PluginSettingsPane(Res.string.settings_plugins_header) {
	var enabled by remember { mutableStateOf(true) }
	HdHairlineToggleRow(
		checked = enabled,
		onCheckedChange = { enabled = it },
		label = "Enable example plugin",
		hint = "A plugin's own settings pane renders here.",
	)
}

@Preview
@Composable
internal fun PluginSettingsSectionPreview() {
	AppTheme(globalSettingsPreview) {
		HdHairlineSection(
			section = 8,
			title = "Plugins",
			modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(24.dp),
			contentSpacing = 24.dp,
		) {
			PluginSettingsSection(listOf(previewPane))
		}
	}
}
