package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.StringResource
import org.koin.dsl.module

class PluginSettingsPane(val name: @Composable () -> String, val content: @Composable ColumnScope.() -> Unit)

/** The registered plugin UI halves whose plugin is also registered. */
class PluginUiRegistry(uis: List<PluginUi>, private val pluginRegistry: PluginRegistry) {
	val uis: List<PluginUi> = run {
		val pluginIds = pluginRegistry.plugins.map { it.id }.toSet()
		val (paired, orphaned) = uis.partition { it.id in pluginIds }
		orphaned.forEach { Napier.w { "Plugin UI '${it.id}' has no registered plugin; ignoring it" } }
		paired
	}

	/** One per plugin with declared settings or a custom pane: the declared form first, then the pane. */
	val settingsPanes: List<PluginSettingsPane> = pluginRegistry.plugins.mapNotNull { plugin ->
		val ui = this.uis.firstOrNull { it.id == plugin.id }
		val custom = ui?.settingsPane
		if (plugin.settings().isEmpty() && custom == null) return@mapNotNull null
		PluginSettingsPane(name = { ui?.name?.get() ?: plugin.name ?: plugin.id }) {
			if (plugin.settings().isNotEmpty()) DeclaredSettings(plugin, ui)
			custom?.invoke(this)
		}
	}

	val exportFormatLabels: Map<String, StringResource> = this.uis.fold(emptyMap()) { labels, ui ->
		labels + ui.exportFormatLabels()
	}

	val projectActions: List<ProjectAction> = this.uis.flatMap { it.projectActions() }

	@Composable
	private fun ColumnScope.DeclaredSettings(plugin: ClientPlugin, ui: PluginUi?) {
		val store = pluginRegistry.settings(plugin.id) ?: return
		val values by store.values.collectAsState()
		DeclaredSettingsForm(
			declarations = store.declarations,
			values = values,
			labels = ui?.settingLabels().orEmpty(),
			onChange = store::set,
		)
	}
}

fun pluginUiModule(uis: List<PluginUi>) = module {
	single { PluginUiRegistry(uis, get()) }
}
