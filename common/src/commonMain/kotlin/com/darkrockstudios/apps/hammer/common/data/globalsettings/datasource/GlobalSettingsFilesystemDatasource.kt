package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository.Companion.createDefault
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath

class GlobalSettingsFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : GlobalSettingsDatasource {

	init {
		if (!fileSystem.exists(CONFIG_PATH)) {
			val default = createDefault()
			storeSettings(default)
		}
	}

	override fun loadSettings(): GlobalSettings {
		Napier.i { "Loading Global Settings from: $CONFIG_PATH" }

		val settingsText: String = fileSystem.read(CONFIG_PATH) {
			readUtf8()
		}

		val settings: GlobalSettings = try {
			toml.decodeFromString(settingsText)
		} catch (e: NumberFormatException) {
			Napier.e("Failed to load Global Settings, Reverting to defaults.", e)
			fileSystem.delete(CONFIG_PATH)
			createDefault()
		} catch (e: SerializationException) {
			Napier.e("Failed to load Global Settings, Reverting to defaults.", e)
			fileSystem.delete(CONFIG_PATH)
			createDefault()
		}
		return settings
	}

	override fun storeSettings(settings: GlobalSettings) {
		val settingsText = toml.encodeToString(settings)

		fileSystem.createDirectories(CONFIG_PATH.parent!!)
		fileSystem.write(CONFIG_PATH, false) {
			writeUtf8(settingsText)
		}
	}

	companion object {
		private const val FILE_NAME = "global_settings.toml"
		val CONFIG_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}