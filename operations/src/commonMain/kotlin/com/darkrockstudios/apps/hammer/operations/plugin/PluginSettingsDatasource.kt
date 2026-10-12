package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/** Each plugin's global settings, one TOML file per plugin under `<config>/plugins/`. */
class PluginSettingsDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val pluginsDirectory: Path = getConfigDirectory().toPath() / PLUGINS_DIRECTORY,
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

	/** A plugin's declared settings as stored, with defaults for anything missing or invalid. */
	fun loadDeclared(pluginId: String, declarations: List<SettingDeclaration>): JsonObject =
		declarations.resolve(load(pluginId, TomlTable.serializer()) { TomlTable(emptyMap<String, TomlElement>()) }.toSettingValues())

	private fun settingsPath(pluginId: String): Path = pluginsDirectory / "$pluginId.toml"

	private fun logLoadFailure(pluginId: String, e: Exception) {
		Napier.e(e) { "Failed to load settings for plugin '$pluginId', using defaults" }
	}

	companion object {
		const val PLUGINS_DIRECTORY = "plugins"
	}
}
