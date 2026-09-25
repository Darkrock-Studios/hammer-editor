package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The runtime plugins installed under [directory]: packages in `packages/`, and whether each is
 * enabled and which operations the user granted it in [STATE_FILE]. Once [activate]d, installs and
 * other changes apply to the plugin registry at once.
 */
class RuntimePlugins(
	private val fileSystem: FileSystem,
	private val directory: Path,
) {
	private val packages = directory / PACKAGES_DIRECTORY
	private val stateFile = directory / STATE_FILE

	private var registry: PluginRegistry? = null

	// Changes take turns, so each reads the state and the registry the last one left.
	private val changes = reentrantLock()

	/** The enabled, readable plugins. Never throws: a plugin that cannot load is logged and skipped. */
	fun load(): List<WasmPlugin> = readState().plugins.mapNotNull { (id, state) -> load(id, state) }

	/**
	 * Adds the enabled plugins to [registry], and from now on applies every change to it. Never throws:
	 * a plugin the registry refuses, such as one adding a command another has, is logged and skipped.
	 */
	fun activate(registry: PluginRegistry) = changes.withLock {
		this.registry = registry
		load().forEach { plugin ->
			try {
				registry.add(plugin)
			} catch (e: IllegalArgumentException) {
				Napier.e { "Runtime plugin '${plugin.id}' was not added: ${e.message}" }
			}
		}
	}

	private fun load(id: String, state: PluginState): WasmPlugin? {
		if (!state.enabled) return null
		return try {
			val plugin = PluginPackage.read(fileSystem, packagePath(id))
			if (plugin.manifest.id != id) throw PluginPackageException("Package id changed to '${plugin.manifest.id}'")
			plugin.toWasmPlugin(state.granted.toSet())
		} catch (e: PluginPackageException) {
			Napier.e { "Runtime plugin '$id' could not load: ${e.message}" }
			null
		} catch (e: IOException) {
			Napier.e(e) { "Runtime plugin '$id' could not load" }
			null
		}
	}

	/** Everything installed, loadable or not, for Settings. */
	fun installed(): List<InstalledPlugin> = readState().plugins.map { (id, state) ->
		val manifest = try {
			PluginPackage.read(fileSystem, packagePath(id)).manifest
		} catch (e: PluginPackageException) {
			null
		} catch (e: IOException) {
			null
		}
		InstalledPlugin(id, manifest, state.enabled, state.granted)
	}

	/** Reads and checks the package at [source] without installing it, for the permission prompt. */
	fun inspect(source: Path): PluginPackage = PluginPackage.read(fileSystem, source, checkModule = true)

	/**
	 * Installs, or replaces, the package at [source], enabled and granted every operation its manifest
	 * requests: the caller has shown them to the user. Pass the result of [inspect] as [inspected] to
	 * skip checking the package again. Refused, installing nothing, when the registry would refuse it.
	 */
	fun install(source: Path, inspected: PluginPackage = inspect(source)): PluginPackage = changes.withLock {
		val plugin = inspected
		val id = plugin.manifest.id
		val granted = plugin.manifest.permissions.operations
		registry?.let { live -> refused { live.validate(plugin.toWasmPlugin(granted.toSet())) } }

		fileSystem.createDirectories(packages)
		val staging = packages / "$id.$STAGING_EXTENSION"
		fileSystem.copy(source, staging)
		fileSystem.atomicMove(staging, packagePath(id))
		val state = PluginState(enabled = true, granted = granted)
		updateState { it + (id to state) }
		registry?.let { live -> apply(live, id, state) }
		plugin
	}

	/** Throws [PluginPackageException], leaving the plugin disabled, when the registry refuses it. */
	fun setEnabled(id: String, enabled: Boolean): Unit = changes.withLock {
		val state = readState().plugins[id]?.copy(enabled = enabled) ?: return
		val live = registry
		if (enabled && live != null) {
			load(id, state)?.let { plugin -> refused { live.validate(plugin) } }
		}
		updateState { it + (id to state) }
		live?.let { apply(it, id, state) }
	}

	/** Removes the package and its record. Its settings file stays, in case it is installed again. */
	fun uninstall(id: String) = changes.withLock {
		registry?.remove(id)
		updateState { it - id }
		fileSystem.delete(packagePath(id), mustExist = false)
	}

	/** Brings [live] in line with [id]'s [state]: its plugin added, replacing any before, or removed. */
	private fun apply(live: PluginRegistry, id: String, state: PluginState) {
		val plugin = load(id, state)
		if (plugin == null) live.remove(id) else refused { live.add(plugin) }
	}

	private inline fun refused(block: () -> Unit) {
		try {
			block()
		} catch (e: IllegalArgumentException) {
			throw PluginPackageException(e.message.orEmpty())
		}
	}

	private fun PluginPackage.toWasmPlugin(granted: Set<String>) = WasmPlugin(
		manifest = manifest,
		loadModule = ::readModule,
		declaredSettings = settings,
		granted = granted,
		savedSettings = { PluginSettingsDatasource(fileSystem, toml, directory).loadDeclared(manifest.id, settings) },
	)

	private fun packagePath(id: String): Path = packages / "$id.${PluginPackage.EXTENSION}"

	private fun readState(): StateFile {
		if (!fileSystem.exists(stateFile)) return StateFile()
		return try {
			toml.decodeFromString(StateFile.serializer(), fileSystem.read(stateFile) { readUtf8() })
		} catch (e: IOException) {
			Napier.e(e) { "Could not read runtime plugin state" }
			StateFile()
		} catch (e: IllegalArgumentException) {
			Napier.e(e) { "Could not read runtime plugin state" }
			StateFile()
		}
	}

	private fun updateState(transform: (Map<String, PluginState>) -> Map<String, PluginState>) {
		val updated = StateFile(transform(readState().plugins))
		fileSystem.createDirectories(directory)
		val staging = directory / "$STATE_FILE.tmp"
		fileSystem.write(staging) { writeUtf8(toml.encodeToString(StateFile.serializer(), updated)) }
		fileSystem.atomicMove(staging, stateFile)
	}

	@Serializable
	private class StateFile(val plugins: Map<String, PluginState> = emptyMap())

	@Serializable
	private data class PluginState(val enabled: Boolean, val granted: List<String> = emptyList())

	companion object {
		const val PACKAGES_DIRECTORY = "packages"

		// Plugin ids cannot start with an underscore, so no plugin's settings file can collide with this.
		const val STATE_FILE = "_runtime-plugins.toml"
		private const val STAGING_EXTENSION = "partial"
		private val toml = Toml { ignoreUnknownKeys = true }

		/** The app's runtime plugins, kept beside plugin settings files in the config directory. */
		fun inConfigDirectory(fileSystem: FileSystem) = RuntimePlugins(
			fileSystem = fileSystem,
			directory = getConfigDirectory().toPath() / PluginSettingsDatasource.PLUGINS_DIRECTORY,
		)
	}

	/** Makes these plugins available to Settings. */
	fun koinModule(): Module = module { single { this@RuntimePlugins } }
}

/** [manifest] is null when the installed package can no longer be read. */
class InstalledPlugin(
	val id: String,
	val manifest: PluginManifest?,
	val enabled: Boolean,
	val granted: List<String>,
)
