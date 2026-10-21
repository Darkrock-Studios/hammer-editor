package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.secrets.SecretStore
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Each plugin's global settings, one TOML file per plugin under `<config>/plugins/`, apart from its
 * [SettingDeclaration.Secret]s, which go in [secrets]. Without one, a secret lasts only the session.
 */
class PluginSettingsDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val pluginsDirectory: Path = getConfigDirectory().toPath() / PLUGINS_DIRECTORY,
	private val secrets: SecretStore? = null,
) {
	/** Returns [default] when the file is missing or unreadable; a bad file is replaced on the next [store]. */
	fun <T> load(pluginId: String, serializer: KSerializer<T>, default: () -> T): T {
		val path = settingsPath(pluginId)
		if (!fileSystem.exists(path)) return default()

		return try {
			toml.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
		} catch (e: IOException) {
			logLoadFailure(pluginId, e)
			default()
		} catch (e: SerializationException) {
			logLoadFailure(pluginId, e)
			default()
		} catch (e: IllegalArgumentException) {
			logLoadFailure(pluginId, e)
			default()
		} catch (e: IllegalStateException) {
			logLoadFailure(pluginId, e)
			default()
		}
	}

	fun <T> store(pluginId: String, serializer: KSerializer<T>, value: T) {
		fileSystem.createDirectories(pluginsDirectory)
		val path = settingsPath(pluginId)
		val staging = pluginsDirectory / "$pluginId.toml.tmp"
		fileSystem.write(staging) {
			writeUtf8(toml.encodeToString(serializer, value))
		}
		fileSystem.atomicMove(staging, path)
	}

	/**
	 * A plugin's declared settings as stored, with defaults for anything missing or invalid. A secret
	 * found in the settings file, saved there before its setting was one, moves to [secrets].
	 */
	fun loadDeclared(pluginId: String, declarations: List<SettingDeclaration>): JsonObject {
		val table = load(pluginId, TomlTable.serializer()) { TomlTable(emptyMap<String, TomlElement>()) }
		val stored = table.toSettingValues()
		val secretKeys = declarations.filterIsInstance<SettingDeclaration.Secret>().map { it.key }.toSet()
		val found = secretKeys.mapNotNull { key ->
			val value = secrets?.get(secretKey(pluginId, key)) ?: stored[key]?.takeIf { it.isString }?.content
			value?.let { key to JsonPrimitive(it) }
		}.toMap()
		val plaintext = secretKeys.filter { it in table }
		if (secrets != null && plaintext.isNotEmpty()) {
			plaintext.forEach { key -> found[key]?.let { secrets.put(secretKey(pluginId, key), it.content) } }
			try {
				store(pluginId, TomlTable.serializer(), TomlTable(table - plaintext.toSet()))
			} catch (e: IOException) {
				Napier.e(e) { "Failed to remove secrets from the settings file of plugin '$pluginId'" }
			}
		}
		return declarations.resolve(stored + found)
	}

	/** Saves [values]: secrets to [secrets], and everything else to the plugin's settings file. */
	fun storeDeclared(pluginId: String, declarations: List<SettingDeclaration>, values: JsonObject) {
		val secretKeys = declarations.filterIsInstance<SettingDeclaration.Secret>().map { it.key }.toSet()
		secretKeys.forEach { key ->
			(values[key] as? JsonPrimitive)?.let { secrets?.put(secretKey(pluginId, key), it.content) }
		}
		store(pluginId, TomlTable.serializer(), JsonObject(values - secretKeys).toTomlTable())
	}

	/** Forgets every secret [pluginId] has, as when it is uninstalled. */
	fun removeSecrets(pluginId: String) {
		secrets?.removeAll(secretPrefix(pluginId))
	}

	private fun settingsPath(pluginId: String): Path = pluginsDirectory / "$pluginId.toml"

	private fun logLoadFailure(pluginId: String, e: Exception) {
		Napier.e(e) { "Failed to load settings for plugin '$pluginId', using defaults" }
	}

	companion object {
		const val PLUGINS_DIRECTORY = "plugins"

		// Plugin ids have no slashes, so no plugin's prefix is another's.
		private fun secretPrefix(pluginId: String) = "plugin/$pluginId/"

		private fun secretKey(pluginId: String, key: String) = secretPrefix(pluginId) + key
	}
}
