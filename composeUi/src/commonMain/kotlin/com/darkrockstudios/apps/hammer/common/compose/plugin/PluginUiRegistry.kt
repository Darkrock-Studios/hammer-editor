package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.StringResource
import org.koin.dsl.module

class PluginSettingsPane(val name: StringResource, val content: @Composable ColumnScope.() -> Unit)

/** The registered plugin UI halves whose plugin is also registered. */
class PluginUiRegistry(uis: List<PluginUi>, pluginRegistry: PluginRegistry) {
	val uis: List<PluginUi> = run {
		val pluginIds = pluginRegistry.plugins.map { it.id }.toSet()
		val (paired, orphaned) = uis.partition { it.id in pluginIds }
		orphaned.forEach { Napier.w { "Plugin UI '${it.id}' has no registered plugin; ignoring it" } }
		paired
	}

	val settingsPanes: List<PluginSettingsPane> =
		this.uis.mapNotNull { ui -> ui.settingsPane?.let { PluginSettingsPane(ui.name, it) } }

	val exportFormatLabels: Map<String, StringResource> = this.uis.fold(emptyMap()) { labels, ui ->
		labels + ui.exportFormatLabels()
	}
}

fun pluginUiModule(uis: List<PluginUi>) = module {
	single { PluginUiRegistry(uis, get()) }
}
