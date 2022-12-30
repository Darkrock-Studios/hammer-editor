package com.darkrockstudios.apps.hammer.common.globalsettings

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path.Companion.toPath

class GlobalSettingsRepository(
	private val fileSystem: FileSystem,
	private val toml: Toml
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
		_globalSettingsUpdates.tryEmit(settings)
	}

	private fun writeSettings(settings: GlobalSettings) {
		val settingsText = toml.encodeToString(settings)

		fileSystem.createDirectories(CONFIG_PATH.parent!!)
		fileSystem.write(CONFIG_PATH, false) {
			writeUtf8(settingsText)
		}
	}

	private fun loadSettings(): GlobalSettings {
		lateinit var settingsText: String
		fileSystem.read(CONFIG_PATH) {
			settingsText = readUtf8()
		}

		val settings: GlobalSettings = toml.decodeFromString(settingsText)
		return settings
	}

	companion object {
		private const val FILE_NAME = "global_settings.toml"
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