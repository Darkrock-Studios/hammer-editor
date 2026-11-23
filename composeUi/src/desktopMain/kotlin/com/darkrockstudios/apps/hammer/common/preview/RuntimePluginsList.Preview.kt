package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.projectselection.settings.RuntimePluginsList
import com.darkrockstudios.apps.hammer.plugins.wasmhost.InstalledPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest

/** The Plugins tab's list: one active with settings, one off, one turned off after a crash. */
@Composable
internal fun ColumnScope.RuntimePluginsListPreview(showHeader: Boolean = true) {
	Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
		RuntimePluginsList(
			installed = previewInstalledPlugins,
			failure = null,
			hasSettings = { it == "grammar" },
			onEnabledChange = { _, _ -> },
			onDetails = {},
			onSettings = {},
			onUninstall = {},
			onInstall = {},
			showHeader = showHeader,
		)
	}
}

private val previewInstalledPlugins = listOf(
	InstalledPlugin(
		id = "grammar",
		manifest = PluginManifest(id = "grammar", name = "Grammar check", version = "0.8.0", api = 1),
		enabled = true,
		granted = emptyList(),
	),
	InstalledPlugin(
		id = "fantasy-names",
		manifest = PluginManifest(id = "fantasy-names", name = "Fantasy names", version = "1.2.0", api = 1),
		enabled = false,
		granted = emptyList(),
	),
	InstalledPlugin(
		id = "character-interview",
		manifest = PluginManifest(id = "character-interview", name = "Character interview", version = "0.3.1", api = 1),
		enabled = false,
		granted = emptyList(),
		turnedOffAfterCrash = true,
	),
)
