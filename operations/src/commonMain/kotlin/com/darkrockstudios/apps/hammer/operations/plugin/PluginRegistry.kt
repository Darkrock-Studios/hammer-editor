package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectLifecycleListener
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcherNow
import com.darkrockstudios.apps.hammer.operations.KoinProjectResolver
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * The plugins active in this process. Built before Koin starts; contributes their Koin modules and
 * relays app and project lifecycle events to them.
 */
class PluginRegistry(val plugins: List<ClientPlugin>) : ProjectLifecycleListener, KoinComponent {

	init {
		plugins.forEach { require(PLUGIN_ID.matches(it.id)) { "Invalid plugin id '${it.id}'" } }
		val duplicates = plugins.groupBy { it.id }.filterValues { it.size > 1 }.keys
		require(duplicates.isEmpty()) { "Duplicate plugin ids: $duplicates" }
		plugins.forEach { Napier.i { "Client plugin '${it.id}' installed" } }
	}

	private val exporters: List<StoryExporter> = plugins.flatMap { plugin ->
		plugin.exporters().onEach { exporter ->
			require(exporter.formatId.startsWith("${plugin.id}.")) {
				"Plugin '${plugin.id}' export format '${exporter.formatId}' must start with '${plugin.id}.'"
			}
		}
	}

	private val operations: List<Operation<*, *>> = plugins.flatMap { plugin ->
		plugin.operations().onEach { op ->
			require(op.name.startsWith("${plugin.id}.")) {
				"Plugin '${plugin.id}' operation '${op.name}' must start with '${plugin.id}.'"
			}
		}
	}

	/** Built here so a bad plugin operation fails at startup, not on first use. */
	private val operationRegistry = OperationRegistry(coreOperations() + operations, KoinProjectResolver())

	private val openProjects = MutableStateFlow<Map<ProjectDef, OpenProject>>(emptyMap())

	private class OpenProject(
		val job: Job,
		val contexts: Map<String, ProjectPluginContext>,
	)

	fun koinModules(): List<Module> {
		val registry = this
		val own = module {
			single { registry } bind ProjectLifecycleListener::class
			single { PluginSettingsDatasource(get(), get()) }
			single { operationRegistry }
			exporters.forEach { exporter ->
				single<StoryExporter>(named("export:${exporter.formatId}")) { exporter }
			}
		}
		return listOf(own) + plugins.mapNotNull { it.koinModule() }
	}

	/** Call once Koin is up and data migration has run. */
	fun start() {
		val appScope = get<CoroutineScope>(named(APP_SCOPE))
		plugins.forEach { plugin ->
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
		val contexts = plugins.associate { plugin ->
			plugin.id to ProjectPluginContext(
				pluginId = plugin.id,
				projectDef = projectDef,
				projectScope = projectScope,
				coroutineScope = coroutineScope,
				fileSystem = fileSystem,
			)
		}
		openProjects.update { it + (projectDef to OpenProject(job, contexts)) }

		plugins.forEach { plugin ->
			guarded(plugin, "onProjectOpened") { plugin.onProjectOpened(contexts.getValue(plugin.id)) }
		}
	}

	override fun onProjectClosed(projectDef: ProjectDef) {
		val open = openProjects.getAndUpdate { it - projectDef }[projectDef] ?: return

		plugins.forEach { plugin ->
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

	private companion object {
		val PLUGIN_ID = Regex("[a-z0-9][a-z0-9_-]*")
		val STOP_TIMEOUT = 1.seconds
	}
}
