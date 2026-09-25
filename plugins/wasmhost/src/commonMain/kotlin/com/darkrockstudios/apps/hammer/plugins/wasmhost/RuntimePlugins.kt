package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * The runtime plugins installed under [directory]: packages in `packages/`, and whether each is
 * enabled and which operations the user granted it in [STATE_FILE]. A runtime plugin can never take
 * one of [compiledInIds]. Changes apply at the next start, since the plugin registry is fixed for the
 * process.
 */
class RuntimePlugins(
	private val fileSystem: FileSystem,
	private val directory: Path,
	private val compiledInIds: Set<String>,
) {
	private val packages = directory / PACKAGES_DIRECTORY
	private val stateFile = directory / STATE_FILE

	private val _restartNeeded = MutableStateFlow(false)

	/** True once anything was installed, enabled, disabled, or uninstalled since this process started. */
	val restartNeeded: StateFlow<Boolean> = _restartNeeded.asStateFlow()

	/** The enabled, readable plugins. Never throws: a plugin that cannot load is logged and skipped. */
	fun load(): List<ClientPlugin> = readState().plugins.mapNotNull { (id, state) ->
		if (!state.enabled) return@mapNotNull null
		if (id in compiledInIds) {
			Napier.w { "Runtime plugin '$id' shares an id with a compiled-in plugin; not loading it" }
			return@mapNotNull null
		}
		try {
			val plugin = PluginPackage.read(fileSystem, packagePath(id))
			if (plugin.manifest.id != id) throw PluginPackageException("Package id changed to '${plugin.manifest.id}'")
			WasmPlugin(plugin.manifest, plugin.wasm, plugin.settings, granted = state.granted.toSet())
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
	 * skip checking the package again.
	 */
	fun install(source: Path, inspected: PluginPackage = inspect(source)): PluginPackage {
		val plugin = inspected
		val id = plugin.manifest.id
		if (id in compiledInIds) throw PluginPackageException("Plugin id '$id' is taken by a built-in plugin")

		fileSystem.createDirectories(packages)
		val staging = packages / "$id.$STAGING_EXTENSION"
		fileSystem.copy(source, staging)
		fileSystem.atomicMove(staging, packagePath(id))
		updateState { it + (id to PluginState(enabled = true, granted = plugin.manifest.permissions.operations)) }
		return plugin
	}

	fun setEnabled(id: String, enabled: Boolean) {
		updateState { plugins -> plugins[id]?.let { plugins + (id to it.copy(enabled = enabled)) } ?: plugins }
	}

	/** Removes the package and its record. Its settings file stays, in case it is installed again. */
	fun uninstall(id: String) {
		updateState { it - id }
		fileSystem.delete(packagePath(id), mustExist = false)
	}

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
		_restartNeeded.value = true
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
	}
}

/** [manifest] is null when the installed package can no longer be read. */
class InstalledPlugin(
	val id: String,
	val manifest: PluginManifest?,
	val enabled: Boolean,
	val granted: List<String>,
)
