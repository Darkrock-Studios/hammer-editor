package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.validateSettings
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * A `.hammerplugin` package: a zip of `manifest.toml`, `plugin.wasm`, and optionally `settings.toml`.
 * The module, by far the largest part, is read only when [readModule] is called.
 */
class PluginPackage(
	val manifest: PluginManifest,
	val settings: List<SettingDeclaration>,
	private val moduleReader: () -> ByteArray,
) {
	/** Reads the module from the package; each call reads it again, so callers need not keep it. */
	fun readModule(): ByteArray = moduleReader()

	companion object {
		const val EXTENSION = "hammerplugin"
		private const val MANIFEST = "manifest.toml"
		private const val MODULE = "plugin.wasm"
		private const val SETTINGS = "settings.toml"
		private const val MAX_TEXT_BYTES = 256L * 1024
		private const val BYTES_PER_MIB = 1024L * 1024
		private const val MAX_CODE_BYTES = 32 * BYTES_PER_MIB

		/** Words the CLI handles itself. */
		private val RESERVED_COMMANDS = setOf("help")

		/**
		 * Reads and checks the package at [path]: a well-formed manifest for this host's API, formats
		 * prefixed with the plugin's id, and usable settings. [checkModule] also instantiates the module,
		 * which checks its imports and runs its initializer; it is slower and only worth doing at install.
		 */
		fun read(fileSystem: FileSystem, path: Path, checkModule: Boolean = false): PluginPackage {
			val zip = try {
				fileSystem.openZip(path)
			} catch (e: IOException) {
				throw PluginPackageException("Not a plugin package: ${e.message}")
			}
			val root = "/".toPath()
			fun bytes(name: String, limit: Long): ByteArray {
				val size = zip.metadata(root / name).size ?: 0
				if (size > limit) throw PluginPackageException("$name is larger than $limit bytes")
				return zip.read(root / name) { readByteArray() }
			}
			fun text(name: String) = bytes(name, MAX_TEXT_BYTES).decodeToString()

			val manifest = try {
				PluginManifest.parse(text(MANIFEST))
			} catch (e: IOException) {
				throw PluginPackageException("Package has no $MANIFEST")
			} catch (e: IllegalArgumentException) {
				throw PluginPackageException("Invalid $MANIFEST: ${e.message}")
			}
			check(manifest)

			val settings = if (zip.exists(root / SETTINGS)) {
				try {
					parseSettingDeclarations(text(SETTINGS)).also { validateSettings(manifest.id, it) }
				} catch (e: IllegalArgumentException) {
					throw PluginPackageException("Invalid $SETTINGS: ${e.message}")
				}
			} else {
				emptyList()
			}

			val moduleSize = zip.metadataOrNull(root / MODULE)?.size ?: throw PluginPackageException("Package has no $MODULE")
			// A module's data has to fit its memory, so a pre-initialized one may be as large as that.
			val maxModuleBytes = manifest.limits.memory * BYTES_PER_MIB + MAX_CODE_BYTES
			if (moduleSize > maxModuleBytes) throw PluginPackageException("$MODULE is larger than $maxModuleBytes bytes")
			val reader = {
				try {
					fileSystem.openZip(path).read(root / MODULE) { readByteArray() }
				} catch (e: IOException) {
					throw PluginException("Could not read $MODULE: ${e.message}")
				}
			}
			return PluginPackage(manifest, settings, reader).also { if (checkModule) it.checkModule() }
		}

		private fun check(manifest: PluginManifest) {
			if (!PluginRegistry.isValidId(manifest.id)) {
				throw PluginPackageException("Invalid plugin id '${manifest.id}'")
			}
			val duplicates = manifest.exporters.groupBy { it.format }.filterValues { it.size > 1 }.keys
			if (duplicates.isNotEmpty()) throw PluginPackageException("Export formats declared twice: $duplicates")
			manifest.exporters.forEach { exporter ->
				if (!exporter.format.startsWith("${manifest.id}.")) {
					throw PluginPackageException("Export format '${exporter.format}' must start with '${manifest.id}.'")
				}
				if (exporter.input != PluginManifest.INPUT_MARKDOWN && exporter.input != PluginManifest.INPUT_PROSE) {
					throw PluginPackageException("Export format '${exporter.format}' has unknown input '${exporter.input}'")
				}
			}
			manifest.permissions.operations.forEach { entry ->
				if (OperationGrant.parse(entry) == null) throw PluginPackageException("Unknown permission '$entry'")
			}
			checkCommands(manifest)
			val actions = manifest.actions.map { it.name }
			actions.groupBy { it }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
				throw PluginPackageException("Actions declared twice: $it")
			}
			actions.firstOrNull { !PluginRegistry.isValidId(it) }?.let { throw PluginPackageException("Invalid action name '$it'") }
			manifest.actions.forEach { action ->
				if (action.output != PluginManifest.OUTPUT_MESSAGE && action.output != PluginManifest.OUTPUT_DOCUMENT) {
					throw PluginPackageException("Action '${action.name}' has unknown output '${action.output}'")
				}
			}
			if (manifest.limits.memory !in 1..PluginManifest.MAX_MEMORY_MIB) {
				throw PluginPackageException("Memory limit must be from 1 to ${PluginManifest.MAX_MEMORY_MIB} MiB")
			}
			val diagnostics = manifest.diagnostics.map { it.name }
			diagnostics.groupBy { it }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
				throw PluginPackageException("Diagnostics declared twice: $it")
			}
			diagnostics.firstOrNull { !PluginRegistry.isValidId(it) }?.let { throw PluginPackageException("Invalid diagnostics name '$it'") }
		}

		// Checked here, since a clash the plugin registry found would stop Hammer from starting.
		private fun checkCommands(manifest: PluginManifest) {
			val names = manifest.commands.map { it.name }
			val duplicates = names.groupBy { it }.filterValues { it.size > 1 }.keys
			if (duplicates.isNotEmpty()) throw PluginPackageException("Commands declared twice: $duplicates")
			val taken = coreOperations().map { it.name.substringBefore('.') }.toSet() + RESERVED_COMMANDS
			names.forEach { name ->
				if (!PluginRegistry.isValidId(name)) throw PluginPackageException("Invalid command name '$name'")
				if (name in taken) throw PluginPackageException("Command '$name' is taken by Hammer")
			}
		}

		private fun PluginPackage.checkModule() {
			try {
				WasmPlugin(manifest, ::readModule, settings).instantiate()
			} catch (e: PluginException) {
				throw PluginPackageException(e.message.orEmpty())
			} catch (e: IllegalArgumentException) {
				throw PluginPackageException("Unsupported module: ${e.message}")
			} catch (e: IllegalStateException) {
				throw PluginPackageException("Unsupported module: ${e.message}")
			}
		}
	}
}

class PluginPackageException(message: String) : Exception(message)
