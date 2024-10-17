package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSettingsFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
) : ServerSettingsDatasource {

	override fun serverIsSetup(projectsDir: HPath): Boolean {
		return fileSystem.exists(getServerSettingsPath(projectsDir).toOkioPath())
	}

	override fun loadServerSettings(projectsDir: HPath): ServerSettings? {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		Napier.i { "Loading Server Settings from: $path" }

		return if (fileSystem.exists(path)) {
			val settingsText: String = fileSystem.read(path) {
				readUtf8()
			}

			try {
				json.decodeFromString(settingsText)
			} catch (e: SerializationException) {
				Napier.e("Failed to load Server Settings, removing invalid file.", e)
				fileSystem.delete(path)
				null
			}
		} else {
			null
		}
	}

	override fun storeServerSettings(settings: ServerSettings, projectsDir: HPath) {
		val settingsText = json.encodeToString(settings)

		val path = getServerSettingsPath(projectsDir).toOkioPath()
		fileSystem.createDirectories(path.parent!!)
		fileSystem.write(path, false) {
			writeUtf8(settingsText)
		}
	}

	override fun removeServerSettings(projectsDir: HPath) {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		fileSystem.delete(path)
	}

	private fun getServerSettingsPath(projectsDir: HPath): HPath {
		return (projectsDir.toOkioPath() / SERVER_FILE_NAME).toHPath()
	}

	companion object {
		const val SERVER_FILE_NAME = "server.json"
	}
}