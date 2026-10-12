package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins

/** The runtime plugins, each with its settings in a dialog, where the platform can install them. */
@Composable
internal fun ColumnScope.PluginSettingsSection(panes: List<PluginSettingsPane>, runtimePlugins: RuntimePlugins) {
	var open by remember { mutableStateOf<String?>(null) }

	RuntimePluginsSection(
		runtimePlugins = runtimePlugins,
		settings = { id -> panes.firstOrNull { it.pluginId == id } },
		onOpenSettings = { id -> open = id },
	)

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
			title = current.name,
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
