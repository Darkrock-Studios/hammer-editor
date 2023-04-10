package com.darkrockstudios.apps.hammer.common.data.globalsettings

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent

class GlobalSettingsRepository(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val json: Json,
) : KoinComponent {

	private val lock = Mutex()

	var globalSettings: GlobalSettings
		private set

	private val _globalSettingsUpdates = MutableSharedFlow<GlobalSettings>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val globalSettingsUpdates: SharedFlow<GlobalSettings> = _globalSettingsUpdates

	private val _serverSettingsUpdates = MutableSharedFlow<ServerSettings?>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val serverSettingsUpdates: SharedFlow<ServerSettings?> = _serverSettingsUpdates

	var serverSettings: ServerSettings?
		private set

	init {
		if (!fileSystem.exists(CONFIG_PATH)) {
			val default = createDefault()
			writeSettings(default)
		}

		globalSettings = loadSettings()
		_globalSettingsUpdates.tryEmit(globalSettings)

		serverSettings = loadServerSettings()
		_serverSettingsUpdates.tryEmit(serverSettings)
	}

	suspend fun updateSettings(action: (GlobalSettings) -> GlobalSettings) {
		val settings = globalSettingsUpdates.first()
		lock.withLock {
			val updated = action(settings)
			dispatchSettingsUpdate(updated)
		}
	}

	private fun dispatchSettingsUpdate(settings: GlobalSettings) {
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

	fun updateServerSettings(settings: ServerSettings) {
		writeServerSettings(settings)
		serverSettings = settings
		_serverSettingsUpdates.tryEmit(settings)
	}

	private fun getServerSettingsPath(): HPath {
		return (globalSettings.projectsDirectory.toPath() / SERVER_FILE_NAME).toHPath()
	}

	private fun loadServerSettings(): ServerSettings? {
		var serverSettings: ServerSettings? = null

		val path = getServerSettingsPath().toOkioPath()
		if (fileSystem.exists(path)) {
			lateinit var settingsText: String
			fileSystem.read(path) {
				settingsText = readUtf8()
			}

			serverSettings = json.decodeFromString(settingsText)
		}
		return serverSettings
	}

	private fun writeServerSettings(settings: ServerSettings) {
		val settingsText = json.encodeToString(settings)

		val path = getServerSettingsPath().toOkioPath()
		fileSystem.createDirectories(path.parent!!)
		fileSystem.write(path, false) {
			writeUtf8(settingsText)
		}
	}

	fun deleteServerSettings() {
		val path = getServerSettingsPath().toOkioPath()
		fileSystem.delete(path)

		serverSettings = null
		_serverSettingsUpdates.tryEmit(null)
	}

	companion object {
		private const val FILE_NAME = "global_settings.toml"
		private val CONFIG_PATH = getConfigDirectory().toPath() / FILE_NAME

		const val DEFAULT_PROJECTS_DIR = "HammerProjects"

		private fun defaultProjectDir() = getDefaultRootDocumentDirectory().toPath() / DEFAULT_PROJECTS_DIR

		fun createDefault(): GlobalSettings {
			return GlobalSettings(
				projectsDirectory = defaultProjectDir().toString()
			)
		}

		private const val SERVER_FILE_NAME = "server.json"
	}
}