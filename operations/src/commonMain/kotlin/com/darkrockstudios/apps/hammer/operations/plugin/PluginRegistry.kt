package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectLifecycleListener
import com.darkrockstudios.apps.hammer.common.data.export.ExporterSource
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcherNow
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.operations.KoinProjectResolver
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * The plugins active in this process. Built before Koin starts with the [compiledIn] plugins, which
 * contribute Koin modules and operations and get app and project lifecycle events. Plugins [add]ed
 * later, such as runtime plugins installed from Settings, can come and go while the app runs, so they
 * contribute only what is looked up live: export formats, settings, and CLI commands.
 */
class PluginRegistry(private val compiledIn: List<ClientPlugin>) : ProjectLifecycleListener, ExporterSource, KoinComponent {

	init {
		compiledIn.forEachIndexed { i, plugin -> checkPlugin(plugin, compiledIn.take(i)) }
		compiledIn.forEach { Napier.i { "Client plugin '${it.id}' installed" } }
	}

	private val operations: List<Operation<*, *>> = compiledIn.flatMap { plugin ->
		plugin.operations().onEach { op ->
			require(op.name.startsWith("${plugin.id}.")) {
				"Plugin '${plugin.id}' operation '${op.name}' must start with '${plugin.id}.'"
			}
		}
	}

	/** Built here so a bad plugin operation fails at startup, not on first use. */
	val operationRegistry = OperationRegistry(coreOperations() + operations, KoinProjectResolver())

	private val operationWords = operationRegistry.operations.map { it.name.substringBefore('.') }.toSet()

	init {
		checkCommands(compiledIn)
	}

	private val _active = MutableStateFlow(compiledIn)

	/** Every active plugin: the compiled-in ones, then those added since. */
	val active: StateFlow<List<ClientPlugin>> = _active.asStateFlow()

	val plugins: List<ClientPlugin> get() = active.value

	/** Every active plugin's CLI commands. */
	val cliCommands: List<CliCommand> get() = plugins.flatMap { it.cliCommands() }

	override fun exporters(): List<StoryExporter> = plugins.flatMap { it.exporters() }

	/**
	 * Adds [plugin], replacing an added plugin with its id. Throws [IllegalArgumentException], changing
	 * nothing, when it is invalid or clashes with another plugin; [validate] finds that out beforehand.
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

	/** The plugins [plugin] would join, once checked against them. */
	private fun validated(plugin: ClientPlugin, current: List<ClientPlugin>): List<ClientPlugin> {
		val others = current.filterNot { it.id == plugin.id && it !in compiledIn }
		checkPlugin(plugin, others)
		require(plugin.operations().isEmpty() && plugin.koinModule() == null) {
			"Plugin '${plugin.id}' adds operations or a Koin module, so it must be compiled in"
		}
		checkCommands(others + plugin)
		return others
	}

	/** Removes an added plugin; compiled-in plugins stay. */
	fun remove(pluginId: String) {
		_active.update { current -> current.filterNot { it.id == pluginId && it !in compiledIn } }
		if (plugins.none { it.id == pluginId }) settingsStores.update { it - pluginId }
	}

	private fun checkPlugin(plugin: ClientPlugin, others: List<ClientPlugin>) {
		require(isValidId(plugin.id)) { "Invalid plugin id '${plugin.id}'" }
		require(others.none { it.id == plugin.id }) { "Duplicate plugin id '${plugin.id}'" }
		validateSettings(plugin.id, plugin.settings())
		plugin.exporters().forEach { exporter ->
			require(exporter.formatId.startsWith("${plugin.id}.")) {
				"Plugin '${plugin.id}' export format '${exporter.formatId}' must start with '${plugin.id}.'"
			}
		}
	}

	private fun checkCommands(plugins: List<ClientPlugin>) {
		val commands = plugins.flatMap { it.cliCommands() }
		val duplicates = commands.groupBy { it.name }.filterValues { it.size > 1 }.keys
		require(duplicates.isEmpty()) { "CLI commands registered more than once: $duplicates" }
		commands.forEach { command ->
			require(isValidId(command.name)) { "Invalid CLI command name '${command.name}'" }
			require(command.name !in operationWords) { "CLI command '${command.name}' would shadow operations" }
		}
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

	private val openProjects = MutableStateFlow<Map<ProjectDef, OpenProject>>(emptyMap())

	private class OpenProject(
		val job: Job,
		val contexts: Map<String, ProjectPluginContext>,
	)

	fun koinModules(): List<Module> {
		val registry = this
		val own = module {
			single { registry } binds arrayOf(ProjectLifecycleListener::class, ExporterSource::class)
			single { PluginSettingsDatasource(get(), get()) }
			single { operationRegistry }
		}
		return listOf(own) + compiledIn.mapNotNull { it.koinModule() }
	}

	/** Call once Koin is up and data migration has run. */
	fun start() {
		val appScope = get<CoroutineScope>(named(APP_SCOPE))
		compiledIn.forEach { plugin ->
			guarded(plugin, "onAppStart") { plugin.onAppStart(appScope) }
		}
	}

	/** The context [pluginId] was given for [projectDef], while that project is open for editing. */
	fun contextFor(pluginId: String, projectDef: ProjectDef): ProjectPluginContext? =
		openProjects.value[projectDef]?.contexts?.get(pluginId)

	override fun onProjectOpened(projectDef: ProjectDef, projectScope: Scope) {
		val job = SupervisorJob()
		val coroutineScope = CoroutineScope(job + injectDefaultDispatcherNow())
		val fileSystem = get<FileSystem>()
		val contexts = compiledIn.associate { plugin ->
			plugin.id to ProjectPluginContext(
				pluginId = plugin.id,
				projectDef = projectDef,
				projectScope = projectScope,
				coroutineScope = coroutineScope,
				fileSystem = fileSystem,
			)
		}
		openProjects.update { it + (projectDef to OpenProject(job, contexts)) }

		compiledIn.forEach { plugin ->
			guarded(plugin, "onProjectOpened") { plugin.onProjectOpened(contexts.getValue(plugin.id)) }
		}
	}

	override fun onProjectClosed(projectDef: ProjectDef) {
		val open = openProjects.getAndUpdate { it - projectDef }[projectDef] ?: return

		compiledIn.forEach { plugin ->
			guarded(plugin, "onProjectClosed") { plugin.onProjectClosed(open.contexts.getValue(plugin.id)) }
		}

		// The project scope closes as soon as this returns, so plugin work must have stopped by then.
		val stopped = runBlocking { withTimeoutOrNull(STOP_TIMEOUT) { open.job.cancelAndJoin() } }
		if (stopped == null) Napier.w { "Plugin work for ${projectDef.name} did not stop within $STOP_TIMEOUT" }
	}

	// One plugin's failure must not take down the app or the other plugins.
	@Suppress("TooGenericExceptionCaught")
	private inline fun guarded(plugin: ClientPlugin, hook: String, block: () -> Unit) {
		try {
			block()
		} catch (e: Exception) {
			Napier.e(e) { "Plugin '${plugin.id}' failed in $hook" }
		}
	}

	companion object {
		private val PLUGIN_ID = Regex("[a-z0-9][a-z0-9_-]*")
		private val STOP_TIMEOUT = 1.seconds

		/** Lowercase and directory-safe: letters, digits, `-` and `_`, starting with a letter or digit. */
		fun isValidId(id: String): Boolean = PLUGIN_ID.matches(id)
	}
}
