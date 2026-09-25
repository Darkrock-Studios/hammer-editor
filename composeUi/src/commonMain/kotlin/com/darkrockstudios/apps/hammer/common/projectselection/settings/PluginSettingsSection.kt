package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane

@Composable
internal fun PluginSettingsSection(panes: List<PluginSettingsPane>) {
	panes.forEach { pane ->
		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			HdMonoLabel(
				text = pane.name(),
				color = MaterialTheme.colorScheme.onSurface,
			)
			pane.content(this)
		}
	}
}
