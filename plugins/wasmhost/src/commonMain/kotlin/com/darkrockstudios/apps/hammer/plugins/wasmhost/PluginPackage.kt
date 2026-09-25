package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.validateSettings
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/** A `.hammerplugin` package: a zip of `manifest.toml`, `plugin.wasm`, and optionally `settings.toml`. */
class PluginPackage(
	val manifest: PluginManifest,
	val wasm: ByteArray,
	val settings: List<SettingDeclaration>,
) {
	companion object {
		const val EXTENSION = "hammerplugin"
		private const val MANIFEST = "manifest.toml"
		private const val MODULE = "plugin.wasm"
		private const val SETTINGS = "settings.toml"
		private const val MAX_TEXT_BYTES = 256L * 1024
		private const val MAX_MODULE_BYTES = 32L * 1024 * 1024

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

			val wasm = try {
				bytes(MODULE, MAX_MODULE_BYTES)
			} catch (e: IOException) {
				throw PluginPackageException("Package has no $MODULE")
			}
			return PluginPackage(manifest, wasm, settings).also { if (checkModule) it.checkModule() }
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
			}
		}

		private fun PluginPackage.checkModule() {
			try {
				WasmPlugin(manifest, wasm, settings).instantiate()
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
