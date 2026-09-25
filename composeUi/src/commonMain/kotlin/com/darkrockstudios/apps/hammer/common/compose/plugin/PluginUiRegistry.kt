package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_command_run
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.StringResource
import org.koin.dsl.module

class PluginSettingsPane(
	val pluginId: String,
	/** Compiled in, so it has no install controls. */
	val compiledIn: Boolean,
	val name: @Composable () -> String,
	val content: @Composable ColumnScope.() -> Unit,
)

/**
 * The registered plugin UI halves whose plugin is also registered. [cliLauncher] runs `hammer` where
 * there is a CLI, to show how to run plugins' commands.
 */
class PluginUiRegistry(
	uis: List<PluginUi>,
	private val pluginRegistry: PluginRegistry,
	private val cliLauncher: List<String>? = null,
) {
	val uis: List<PluginUi> = run {
		val pluginIds = pluginRegistry.plugins.map { it.id }.toSet()
		val (paired, orphaned) = uis.partition { it.id in pluginIds }
		orphaned.forEach { Napier.w { "Plugin UI '${it.id}' has no registered plugin; ignoring it" } }
		paired
	}

	/**
	 * One per active plugin with declared settings, a custom pane, or CLI commands to show: the declared
	 * form, then the pane, then how to run each command. Follows plugins added and removed while running.
	 */
	@Composable
	fun settingsPanes(): List<PluginSettingsPane> {
		val plugins by pluginRegistry.active.collectAsState()
		return remember(plugins) { plugins.mapNotNull(::settingsPane) }
	}

	private fun settingsPane(plugin: ClientPlugin): PluginSettingsPane? {
		val ui = this.uis.firstOrNull { it.id == plugin.id }
		val custom = ui?.settingsPane
		val commands = if (cliLauncher != null) plugin.cliCommands() else emptyList()
		if (plugin.settings().isEmpty() && custom == null && commands.isEmpty()) return null
		return PluginSettingsPane(
			pluginId = plugin.id,
			compiledIn = pluginRegistry.isCompiledIn(plugin.id),
			name = { ui?.name?.get() ?: plugin.name ?: plugin.id },
		) {
			if (plugin.settings().isNotEmpty()) DeclaredSettings(plugin, ui)
			custom?.invoke(this)
			commands.forEach { command ->
				Text(Res.string.plugin_command_run.get(command.help), style = MaterialTheme.typography.bodyMedium)
				SelectionContainer {
					Text(
						text = (cliLauncher.orEmpty() + command.name).joinToString(" ", transform = ::shellQuoted),
						style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
					)
				}
			}
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

private fun shellQuoted(word: String): String =
	if (word.all { it.isLetterOrDigit() || it in "/._-" }) word else "'" + word.replace("'", "'\\''") + "'"

fun pluginUiModule(uis: List<PluginUi>, cliLauncher: List<String>? = null) = module {
	single { PluginUiRegistry(uis, get(), cliLauncher) }
}
