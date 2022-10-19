package com.darkrockstudios.apps.hammer.common.globalsettings

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

class GlobalSettingsRepository(
	private val fileSystem: FileSystem
) {
	var globalSettings: GlobalSettings
		private set

	private val _globalSettingsUpdates = MutableSharedFlow<GlobalSettings>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val globalSettingsUpdates: SharedFlow<GlobalSettings> = _globalSettingsUpdates

	init {
		if (!fileSystem.exists(CONFIG_PATH)) {
			val default = createDefault()
			writeSettings(default)
		}

		globalSettings = loadSettings()
	}

	fun updateSettings(settings: GlobalSettings) {
		writeSettings(settings)
		globalSettings = settings
	}

	private fun writeSettings(settings: GlobalSettings) {
		val settingsJson = Json.encodeToString(settings)

		fileSystem.createDirectories(CONFIG_PATH.parent!!)
		fileSystem.write(CONFIG_PATH, false) {
			writeUtf8(settingsJson)
		}
	}

	private fun loadSettings(): GlobalSettings {
		lateinit var settingsJson: String
		fileSystem.read(CONFIG_PATH) {
			settingsJson = readUtf8()
		}

		val settings: GlobalSettings = Json.decodeFromString(settingsJson)
		return settings
	}

	companion object {
		private const val FILE_NAME = "global_settings.json"
		private val CONFIG_PATH = getConfigDirectory().toPath() / FILE_NAME

		const val DEFAULT_PROJECTS_DIR = "HammerProjects"

		private fun defaultProjectDir() = getRootDocumentDirectory().toPath() / DEFAULT_PROJECTS_DIR

		fun createDefault(): GlobalSettings {
			return GlobalSettings(
				projectsDirectory = defaultProjectDir().toString()
			)
		}
	}
}