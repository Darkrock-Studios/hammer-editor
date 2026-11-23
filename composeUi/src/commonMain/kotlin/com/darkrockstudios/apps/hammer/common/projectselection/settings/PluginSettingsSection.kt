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
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUiRegistry
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform.getKoin

/** The runtime plugins, each with its settings in a dialog, where the platform can install them. */
@Composable
internal fun ColumnScope.PluginSettingsSection(
	panes: List<PluginSettingsPane>,
	runtimePlugins: RuntimePlugins,
	showHeader: Boolean = true,
) {
	var open by remember { mutableStateOf<String?>(null) }

	RuntimePluginsSection(
		runtimePlugins = runtimePlugins,
		settings = { id -> panes.firstOrNull { it.pluginId == id } },
		onOpenSettings = { id -> open = id },
		showHeader = showHeader,
	)

	// Closes itself if the plugin goes away, such as when it is disabled.
	PluginSettingsDialog(pane = panes.firstOrNull { it.pluginId == open }, onDismiss = { open = null })
}

/**
 * Opens [ManagePluginsDialog]; nothing where plugins cannot be installed. Plugins are the same for every
 * project, so app Settings and each project's settings both offer it.
 */
@Composable
internal fun ManagePluginsButton() {
	val runtimePlugins = remember { getKoin().getOrNull<RuntimePlugins>() } ?: return
	var open by remember { mutableStateOf(false) }
	HdHairlineButton(label = Res.string.plugins_manage.get(), onClick = { open = true })
	ManagePluginsDialog(visible = open, runtimePlugins = runtimePlugins, onDismiss = { open = false })
}

@Composable
private fun ManagePluginsDialog(visible: Boolean, runtimePlugins: RuntimePlugins, onDismiss: () -> Unit) {
	AnimatedDialog(visible = visible, onCloseRequest = onDismiss) {
		ManagePluginsCard(onClose = { requestDismiss() }) {
			PluginSettingsSection(koinInject<PluginUiRegistry>().settingsPanes(), runtimePlugins, showHeader = false)
		}
	}
}

/** The manage dialog without its window; [content] is the plugin list. */
@Composable
internal fun ManagePluginsCard(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
	HdHairlineDialogShell(
		title = Res.string.plugins_manage_title.get(),
		onClose = onClose,
		closeContentDescription = Res.string.plugins_manage_close.get(),
	) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(24.dp),
			content = content,
		)
	}
}

@Composable
private fun PluginSettingsDialog(pane: PluginSettingsPane?, onDismiss: () -> Unit) {
	// Kept after pane clears, so the dialog still has its content while it animates out.
	var shown by remember { mutableStateOf(pane) }
	if (pane != null) shown = pane
	AnimatedDialog(visible = pane != null, onCloseRequest = onDismiss) {
		val current = shown ?: return@AnimatedDialog
		PluginSettingsCard(pane = current, onClose = { requestDismiss() })
	}
}

/** One plugin's settings without the dialog window. */
@Composable
internal fun PluginSettingsCard(pane: PluginSettingsPane, onClose: () -> Unit) {
	HdHairlineDialogShell(
		title = pane.name,
		onClose = onClose,
		closeContentDescription = Res.string.plugin_settings_close.get(),
	) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			pane.content(this)
		}
	}
}
