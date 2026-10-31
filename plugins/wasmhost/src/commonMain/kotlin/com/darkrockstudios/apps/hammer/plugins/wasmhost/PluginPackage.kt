package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.PLUGIN_WITHHELD_OPERATIONS
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.toActionFields
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
		private val HOST_NAME = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
		private const val MAX_TEXT_BYTES = 256L * 1024
		private const val BYTES_PER_MIB = 1024L * 1024
		private const val MAX_CODE_BYTES = 32 * BYTES_PER_MIB
		const val MAX_RESOURCE_BYTES = 64 * BYTES_PER_MIB

		/** The largest package read: room for the largest module, resources, translations, and the zip's own records. */
		const val MAX_PACKAGE_BYTES = PluginManifest.MAX_MEMORY_MIB * BYTES_PER_MIB + MAX_CODE_BYTES + MAX_RESOURCE_BYTES + 128 * BYTES_PER_MIB

		/** Words the CLI handles itself. */
		private val RESERVED_COMMANDS = setOf("help")

		/**
		 * Reads and checks the package at [path]: a well-formed manifest for this host's API, formats
		 * prefixed with the plugin's id, usable settings, and resources within their limits. [checkModule] also instantiates the module,
		 * which checks its imports and runs its initializer; it is slower and only worth doing at install.
		 */
		fun read(fileSystem: FileSystem, path: Path, checkModule: Boolean = false): PluginPackage {
			val zip = try {
				val size = fileSystem.metadataOrNull(path)?.size ?: 0
				if (size > MAX_PACKAGE_BYTES) throw PluginPackageException("The package is larger than $MAX_PACKAGE_BYTES bytes")
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
			// Every text in a package is TOML, checked before tomlkt can overflow the stack on it.
			fun text(name: String) = bytes(name, MAX_TEXT_BYTES).decodeToString().also(::requireShallowToml)

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
			manifest.permissions.userHosts?.let { checkUserHosts(it, settings) }

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
				if (exporter.cover && !exporter.images) {
					throw PluginPackageException("Export format '${exporter.format}' asks for the cover without images")
				}
			}
			val withheldOps = coreOperations().filter { it.name in PLUGIN_WITHHELD_OPERATIONS }
			manifest.permissions.operations.forEach { entry ->
				val withheld = when (val grant = OperationGrant.parse(entry) ?: throw PluginPackageException("Unknown permission '$entry'")) {
					is OperationGrant.Named -> grant.name in PLUGIN_WITHHELD_OPERATIONS
					is OperationGrant.Scoped -> withheldOps.any { it.scope == grant.scope && it.access == grant.access }
				}
				if (withheld) throw PluginPackageException("'$entry' cannot be granted: plugins may not change the sync account or sync")
			}
			manifest.permissions.http.firstOrNull { !HOST_NAME.matches(it) }?.let {
				throw PluginPackageException("'$it' in permissions.http is not a host name")
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

		// The host has to be one the user enters, since the install prompt names only the setting, and a
		// toggle the user agrees with has to warn and start off.
		private fun checkUserHosts(userHosts: PluginManifest.UserHosts, settings: List<SettingDeclaration>) {
			val setting = settings.firstOrNull { it.key == userHosts.setting }
			if (setting !is SettingDeclaration.Text && setting !is SettingDeclaration.Secret) {
				throw PluginPackageException("permissions.userHosts setting '${userHosts.setting}' is not a declared string or secret setting")
			}
			if (setting.default.content.isNotEmpty()) {
				throw PluginPackageException(
					"permissions.userHosts setting '${userHosts.setting}' must have no default: list a host known in advance in permissions.http"
				)
			}
			val key = userHosts.toggle ?: return
			val toggle = settings.firstOrNull { it.key == key } as? SettingDeclaration.Toggle
			if (toggle == null || !toggle.danger || toggle.defaultValue) {
				throw PluginPackageException("permissions.userHosts toggle '${userHosts.toggle}' must be a bool setting with danger, off by default")
			}
		}

		private fun checkAction(action: PluginManifest.Action) {
			val name = action.name
			if (action.output != null) throw PluginPackageException("Action '$name' declares an output, but actions reply with pages")
			if (action.places.isEmpty()) throw PluginPackageException("Action '$name' appears nowhere: give it places")
			action.places.firstOrNull { ActionPlace.of(it) == null }?.let {
				throw PluginPackageException("Action '$name' has unknown place '$it'")
			}
			try {
				action.field.toActionFields()
			} catch (e: IllegalArgumentException) {
				throw PluginPackageException("Action '$name': ${e.message}")
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
				// Without HTTP, so a package sends nothing before the user agrees to install it.
				WasmPlugin(manifest, ::readModule, settings, readResource = ::readResource, httpClient = null).instantiate()
			} catch (e: PluginException) {
				throw PluginPackageException(e.message.orEmpty())
			} catch (e: IllegalArgumentException) {
				throw PluginPackageException("Unsupported module: ${e.message}")
			} catch (e: IllegalStateException) {
				throw PluginPackageException("Unsupported module: ${e.message}")
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				// A malformed module can trip anything in the runtime, which must not reach the install prompt.
				throw PluginPackageException("The module could not be loaded: ${e::class.simpleName}: ${e.message}")
			}
		}
	}
}

class PluginPackageException(message: String) : Exception(message)
