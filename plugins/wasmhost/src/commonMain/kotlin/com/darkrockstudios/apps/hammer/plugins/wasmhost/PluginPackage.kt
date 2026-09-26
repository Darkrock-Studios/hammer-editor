package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.validateSettings
import okio.BufferedSource
import okio.EOFException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * A `.hammerplugin` package: a zip of `manifest.toml`, `plugin.wasm`, and optionally `settings.toml`,
 * translations in `locales/<tag>.toml`, and files the plugin reads at run time in `resources/`. The
 * module and resources, by far the largest parts, are read only when [readModule] and [readResource]
 * are called.
 */
class PluginPackage(
	val manifest: PluginManifest,
	val settings: List<SettingDeclaration>,
	private val moduleReader: () -> ByteArray,
	/** By language tag, as named in the package. */
	val translations: Map<String, PluginTranslation> = emptyMap(),
	/** Reads a file under `resources/` by its path there, such as `words/en.txt`. */
	private val resourceReader: (String) -> ByteArray? = { null },
) {
	/** Reads the module from the package; each call reads it again, so callers need not keep it. */
	fun readModule(): ByteArray = moduleReader()

	/** Reads the resource at [name] from the package, or null when it has none by that name. */
	fun readResource(name: String): ByteArray? = resourceReader(name)

	/** This package with its manifest's and settings' words in [locale], where it has them. */
	fun localized(locale: String?): PluginPackage {
		val chosen = PluginTranslation.forLocale(translations, locale)
		if (chosen.isEmpty()) return this
		return PluginPackage(translate(manifest, chosen), translate(settings, chosen), moduleReader, translations, resourceReader)
	}

	companion object {
		const val EXTENSION = "hammerplugin"
		private const val MANIFEST = "manifest.toml"
		private const val MODULE = "plugin.wasm"
		private const val SETTINGS = "settings.toml"
		private const val LOCALES = "locales"
		private const val RESOURCES = "resources"
		private const val MAX_RESOURCES = 1000
		private const val MAX_LOCALES = 200
		private val LOCALE_TAG = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*")
		private const val MAX_TEXT_BYTES = 256L * 1024
		private const val BYTES_PER_MIB = 1024L * 1024
		private const val MAX_CODE_BYTES = 32 * BYTES_PER_MIB
		const val MAX_RESOURCE_BYTES = 64 * BYTES_PER_MIB

		/** Words the CLI handles itself. */
		private val RESERVED_COMMANDS = setOf("help")

		/**
		 * Reads and checks the package at [path]: a well-formed manifest for this host's API, formats
		 * prefixed with the plugin's id, usable settings, and resources within their limits. [checkModule] also instantiates the module,
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

			val translations = if (zip.exists(root / LOCALES)) readTranslations(zip.list(root / LOCALES), ::text) else emptyMap()

			val resources = if (zip.exists(root / RESOURCES)) listResources(zip, root / RESOURCES) else emptyMap()

			val moduleSize = zip.metadataOrNull(root / MODULE)?.size ?: throw PluginPackageException("Package has no $MODULE")
			// A module's data has to fit its memory, so a pre-initialized one may be as large as that.
			val maxModuleBytes = manifest.limits.memory * BYTES_PER_MIB + MAX_CODE_BYTES
			if (moduleSize > maxModuleBytes) throw PluginPackageException("$MODULE is larger than $maxModuleBytes bytes")
			// Each read opens the package again, since reinstalling replaces the file under this path.
			fun readEntry(name: String, size: Long): ByteArray = try {
				fileSystem.openZip(path).read(root / name) { readExactly(name, size) }
			} catch (e: IOException) {
				throw PluginException("Could not read $name: ${e.message}")
			}
			return PluginPackage(
				manifest,
				settings,
				{ readEntry(MODULE, moduleSize) },
				translations,
				// Only names listed here are read, so a plugin cannot reach outside resources/.
				{ name -> resources[name]?.let { size -> readEntry("$RESOURCES/$name", size) } },
			).also { if (checkModule) it.checkModule() }
		}

		private fun listResources(zip: FileSystem, directory: Path): Map<String, Long> {
			val files = zip.listRecursively(directory)
				.mapNotNull { path -> zip.metadata(path).takeIf { it.isRegularFile }?.let { path to (it.size ?: 0) } }
				.toList()
			if (files.size > MAX_RESOURCES) throw PluginPackageException("More than $MAX_RESOURCES files in $RESOURCES")
			// Each is checked first, so sizes a package claims cannot overflow the total.
			val total = files.sumOf { (_, size) -> if (size in 0..MAX_RESOURCE_BYTES) size else MAX_RESOURCE_BYTES + 1 }
			if (total > MAX_RESOURCE_BYTES) throw PluginPackageException("$RESOURCES holds more than $MAX_RESOURCE_BYTES bytes")
			return files.associate { (path, size) -> path.relativeTo(directory).segments.joinToString("/") to size }
		}

		private fun readTranslations(files: List<Path>, text: (String) -> String): Map<String, PluginTranslation> {
			val toml = files.filter { it.name.endsWith(".toml") }
			if (toml.size > MAX_LOCALES) throw PluginPackageException("More than $MAX_LOCALES translations in $LOCALES")
			return toml.associate { path ->
				val tag = path.name.removeSuffix(".toml")
				if (!LOCALE_TAG.matches(tag)) throw PluginPackageException("$LOCALES/${path.name} is not named for a language tag")
				val translation = try {
					PluginTranslation.parse(text("$LOCALES/${path.name}"))
				} catch (e: IllegalArgumentException) {
					throw PluginPackageException("Invalid $LOCALES/${path.name}: ${e.message}")
				}
				if ((translation.description?.length ?: 0) > PluginManifest.MAX_DESCRIPTION_LENGTH) {
					throw PluginPackageException("$LOCALES/${path.name}'s description is longer than ${PluginManifest.MAX_DESCRIPTION_LENGTH} characters")
				}
				tag to translation
			}
		}

		// Into one array of the known size: readByteArray() buffers it all and then copies it.
		private fun BufferedSource.readExactly(name: String, size: Long): ByteArray {
			val bytes = ByteArray(size.toInt())
			var read = 0
			while (read < bytes.size) {
				val count = read(bytes, read, bytes.size - read)
				if (count == -1) throw EOFException("$name ended after $read of ${bytes.size} bytes")
				read += count
			}
			return bytes
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
			manifest.actions.forEach(::checkAction)
			if ((manifest.description?.length ?: 0) > PluginManifest.MAX_DESCRIPTION_LENGTH) {
				throw PluginPackageException("The description is longer than ${PluginManifest.MAX_DESCRIPTION_LENGTH} characters")
			}
			manifest.languages.firstOrNull { !LOCALE_TAG.matches(it) }?.let {
				throw PluginPackageException("'$it' in languages is not a language tag")
			}
			if (manifest.limits.memory !in 1..PluginManifest.MAX_MEMORY_MIB) {
				throw PluginPackageException("Memory limit must be from 1 to ${PluginManifest.MAX_MEMORY_MIB} MiB")
			}
			val diagnostics = manifest.diagnostics.map { it.name }
			diagnostics.groupBy { it }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
				throw PluginPackageException("Diagnostics declared twice: $it")
			}
			diagnostics.firstOrNull { !PluginRegistry.isValidId(it) }?.let { throw PluginPackageException("Invalid diagnostics name '$it'") }
			manifest.diagnostics.forEach { check ->
				if (check.scope != PluginManifest.SCOPE_PARAGRAPH && check.scope != PluginManifest.SCOPE_SCENE) {
					throw PluginPackageException("Diagnostics '${check.name}' has unknown scope '${check.scope}'")
				}
			}
		}

		private fun checkAction(action: PluginManifest.Action) {
			val name = action.name
			if (ActionOutput.of(action.output) == null) throw PluginPackageException("Action '$name' has unknown output '${action.output}'")
			if (action.places.isEmpty()) throw PluginPackageException("Action '$name' appears nowhere: give it places")
			action.places.firstOrNull { ActionPlace.of(it) == null }?.let {
				throw PluginPackageException("Action '$name' has unknown place '$it'")
			}
			val fields = action.field.map { table ->
				try {
					table.toActionField()
				} catch (e: IllegalArgumentException) {
					throw PluginPackageException("Action '$name': ${e.message}")
				}
			}
			fields.groupBy { it.key }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
				throw PluginPackageException("Action '$name' declares fields twice: $it")
			}
			fields.forEach { field ->
				if (field.key.isBlank()) throw PluginPackageException("Action '$name' has a field with no key")
				val declaration = (field as? ActionField.Setting)?.declaration ?: return@forEach
				if (declaration is SettingDeclaration.Choice && declaration.options.isEmpty()) {
					throw PluginPackageException("Action '$name' field '${field.key}' is a choice with no options")
				}
				if (declaration.accept(declaration.default) == null) {
					throw PluginPackageException("Action '$name' field '${field.key}' has an invalid default")
				}
			}
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
				WasmPlugin(manifest, ::readModule, settings, readResource = ::readResource).instantiate()
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
