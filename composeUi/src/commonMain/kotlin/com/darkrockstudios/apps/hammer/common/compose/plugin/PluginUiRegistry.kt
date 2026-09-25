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
import com.darkrockstudios.apps.hammer.operations.plugin.ProjectAction
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.dsl.module

class PluginSettingsPane(
	val pluginId: String,
	val name: String,
	val content: @Composable ColumnScope.() -> Unit,
)

/**
 * What active plugins show in the UI, following them as they are added and removed. [cliLauncher]
 * runs `hammer` where there is a CLI, to show how to run plugins' commands.
 */
class PluginUiRegistry(
	private val pluginRegistry: PluginRegistry,
	private val cliLauncher: List<String>? = null,
) {
	/**
	 * One per active plugin with declared settings or CLI commands to show: the declared form, then
	 * how to run each command.
	 */
	@Composable
	fun settingsPanes(): List<PluginSettingsPane> {
		val plugins by pluginRegistry.active.collectAsState()
		return remember(plugins) { plugins.mapNotNull(::settingsPane) }
	}

	/** Every active plugin's items for a project's menu. */
	@Composable
	fun projectActions(): List<ProjectAction> {
		val plugins by pluginRegistry.active.collectAsState()
		return remember(plugins) { plugins.flatMap { it.projectActions() } }
	}

	/**
	 * Every active plugin's checks for the text being written, made anew whenever one of those plugins'
	 * settings changes: the checks read them, so the editor must check its text again.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	fun textDiagnosticsFlow(): Flow<List<TextDiagnosticsProvider>> = pluginRegistry.active.flatMapLatest { plugins ->
		val checking = plugins.filter { it.textDiagnostics().isNotEmpty() }
		val settings = checking.mapNotNull { pluginRegistry.settings(it.id)?.values }
		val changes = if (settings.isEmpty()) flowOf(Unit) else combine(settings) {}
		changes.map { checking.flatMap { it.textDiagnostics() } }
	}

	/** [textDiagnosticsFlow] as state: null until it has the plugins' checks. */
	@Composable
	fun textDiagnostics(): List<TextDiagnosticsProvider>? {
		val flow = remember { textDiagnosticsFlow() }
		return flow.collectAsState(initial = null).value
	}

	private fun settingsPane(plugin: ClientPlugin): PluginSettingsPane? {
		val commands = if (cliLauncher != null) plugin.cliCommands() else emptyList()
		if (plugin.settings().isEmpty() && commands.isEmpty()) return null
		return PluginSettingsPane(pluginId = plugin.id, name = plugin.name ?: plugin.id) {
			if (plugin.settings().isNotEmpty()) DeclaredSettings(plugin)
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

	@Composable
	private fun ColumnScope.DeclaredSettings(plugin: ClientPlugin) {
		val store = pluginRegistry.settings(plugin.id) ?: return
		val values by store.values.collectAsState()
		DeclaredSettingsForm(declarations = store.declarations, values = values, onChange = store::set)
	}
}

internal fun shellQuoted(word: String): String =
	if (word.all { it.isLetterOrDigit() || it in "/._-" }) word else "'" + word.replace("'", "'\\''") + "'"

fun pluginUiModule(cliLauncher: List<String>? = null) = module {
	single { PluginUiRegistry(get(), cliLauncher) }
}
