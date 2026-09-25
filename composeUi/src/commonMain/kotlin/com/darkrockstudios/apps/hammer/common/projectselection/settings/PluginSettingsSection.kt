package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins

/**
 * A row per plugin, whose settings open in a dialog: compiled-in plugins with settings, then the
 * runtime plugins with their install controls, where the platform has them.
 */
@Composable
internal fun ColumnScope.PluginSettingsSection(panes: List<PluginSettingsPane>, runtimePlugins: RuntimePlugins?) {
	var open by remember { mutableStateOf<String?>(null) }

	panes.filter { it.compiledIn }.forEach { pane ->
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(pane.name(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
			HdHairlineButton(label = Res.string.plugin_settings_open.get(), onClick = { open = pane.pluginId })
		}
	}
	runtimePlugins?.let {
		RuntimePluginsSection(
			runtimePlugins = it,
			settings = { id -> panes.firstOrNull { pane -> pane.pluginId == id && !pane.compiledIn } },
			onOpenSettings = { id -> open = id },
		)
	}

	// Closes itself if the plugin goes away, such as when it is disabled.
	PluginSettingsDialog(pane = panes.firstOrNull { it.pluginId == open }, onDismiss = { open = null })
}

@Composable
private fun PluginSettingsDialog(pane: PluginSettingsPane?, onDismiss: () -> Unit) {
	// Kept after pane clears, so the dialog still has its content while it animates out.
	var shown by remember { mutableStateOf(pane) }
	if (pane != null) shown = pane
	AnimatedDialog(visible = pane != null, onCloseRequest = onDismiss) {
		val current = shown ?: return@AnimatedDialog
		HdHairlineDialogShell(
			title = current.name(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.plugin_settings_close.get(),
		) {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				current.content(this)
			}
		}
	}
}
