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
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.plugin.plaintext.PlainTextPluginUi
import com.darkrockstudios.apps.hammer.common.compose.plugin.plaintext.PlainTextSettingsContent
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.PluginSettingsSection
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettings

@Preview
@Composable
internal fun PlainTextSettingsPreview() {
	val pane = PluginSettingsPane(PlainTextPluginUi.name) {
		var settings by remember { mutableStateOf(PlainTextSettings()) }
		PlainTextSettingsContent(settings = settings, onChange = { settings = it(settings) })
	}
	AppTheme(globalSettingsPreview) {
		HdHairlineSection(
			section = 8,
			title = "Plugins",
			modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(24.dp),
			contentSpacing = 24.dp,
		) {
			PluginSettingsSection(listOf(pane))
		}
	}
}
