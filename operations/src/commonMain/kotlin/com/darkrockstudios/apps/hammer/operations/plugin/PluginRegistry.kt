package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.export.ExporterSource
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.operations.KoinProjectResolver
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The plugins active in this process, which can be added and removed while it runs: their export
 * formats, settings, CLI commands, and project actions are looked up live. Also provides the
 * operation registry, which plugins do not add to.
 */
class PluginRegistry : ExporterSource, KoinComponent {

	val operationRegistry = OperationRegistry(coreOperations(), KoinProjectResolver())

	private val operationWords = operationRegistry.operations.map { it.name.substringBefore('.') }.toSet()

	private val _active = MutableStateFlow<List<ClientPlugin>>(emptyList())

	val active: StateFlow<List<ClientPlugin>> = _active.asStateFlow()

	val plugins: List<ClientPlugin> get() = active.value

	/** Every active plugin's CLI commands. */
	val cliCommands: List<CliCommand> get() = plugins.flatMap { it.cliCommands() }

	override fun exporters(): List<StoryExporter> = plugins.flatMap { it.exporters() }

	/**
	 * Adds [plugin], replacing one with its id. Throws [IllegalArgumentException], changing nothing,
	 * when it is invalid or clashes with another plugin; [validate] finds that out beforehand.
	 */
	fun add(plugin: ClientPlugin) {
		_active.update { current -> validated(plugin, current) + plugin }
		settingsStores.update { it - plugin.id }
		Napier.i { "Client plugin '${plugin.id}' added" }
	}

	/** Throws [IllegalArgumentException] if [add] would. */
	fun validate(plugin: ClientPlugin) {
		validated(plugin, plugins)
	}

	fun remove(pluginId: String) {
		_active.update { current -> current.filterNot { it.id == pluginId } }
		settingsStores.update { it - pluginId }
	}

	/** The plugins [plugin] would join, once checked against them. */
	private fun validated(plugin: ClientPlugin, current: List<ClientPlugin>): List<ClientPlugin> {
		val others = current.filterNot { it.id == plugin.id }
		require(isValidId(plugin.id)) { "Invalid plugin id '${plugin.id}'" }
		validateSettings(plugin.id, plugin.settings())
		plugin.exporters().forEach { exporter ->
			require(exporter.formatId.startsWith("${plugin.id}.")) {
				"Plugin '${plugin.id}' export format '${exporter.formatId}' must start with '${plugin.id}.'"
			}
		}
		val commands = (others + plugin).flatMap { it.cliCommands() }
		val duplicates = commands.groupBy { it.name }.filterValues { it.size > 1 }.keys
		require(duplicates.isEmpty()) { "CLI commands registered more than once: $duplicates" }
		commands.forEach { command ->
			require(isValidId(command.name)) { "Invalid CLI command name '${command.name}'" }
			require(command.name !in operationWords) { "CLI command '${command.name}' would shadow operations" }
		}
		return others
	}

	private class SettingsEntry(val plugin: ClientPlugin, val store: DeclaredSettingsStore)

	// Each reads its file on first use, so one plugin's settings never load another's. Keyed by the
	// plugin instance too, so a replaced plugin gets a store for its own declarations.
	private val settingsStores = MutableStateFlow<Map<String, SettingsEntry>>(emptyMap())

	/** The declared settings of [pluginId], or null when it is not active or declares none. Only once Koin is up. */
	fun settings(pluginId: String): DeclaredSettingsStore? {
		val plugin = plugins.firstOrNull { it.id == pluginId }?.takeIf { it.settings().isNotEmpty() } ?: return null
		settingsStores.value[pluginId]?.takeIf { it.plugin === plugin }?.let { return it.store }
		val store = DeclaredSettingsStore(
			pluginId = plugin.id,
			declarations = plugin.settings(),
			datasource = get(),
			ioDispatcher = injectIoDispatcherNow(),
			saveScope = get(named(APP_SCOPE)),
		)
		settingsStores.update { it + (pluginId to SettingsEntry(plugin, store)) }
		return store
	}

	fun koinModule(): Module {
		val registry = this
		return module {
			single { registry } bind ExporterSource::class
			single { PluginSettingsDatasource(get(), get()) }
			single { operationRegistry }
		}
	}

	companion object {
		private val PLUGIN_ID = Regex("[a-z0-9][a-z0-9_-]*")

		/** Lowercase and directory-safe: letters, digits, `-` and `_`, starting with a letter or digit. */
		fun isValidId(id: String): Boolean = PLUGIN_ID.matches(id)
	}
}
