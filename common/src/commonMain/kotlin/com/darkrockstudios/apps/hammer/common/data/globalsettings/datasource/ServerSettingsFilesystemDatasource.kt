package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.base.http.readJsonOrNull
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem

class ServerSettingsFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
) : ServerSettingsDatasource {

	override fun serverIsSetup(projectsDir: HPath): Boolean {
		return fileSystem.exists(getServerSettingsPath(projectsDir).toOkioPath())
	}

	override fun loadServerSettings(projectsDir: HPath): PersistedServerSettings? {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		Napier.i { "Loading Server Settings from: $path" }

		if (!fileSystem.exists(path)) return null

		val settingsText: String = fileSystem.read(path) { readUtf8() }

		return try {
			json.decodeFromString(settingsText)
		} catch (e: SerializationException) {
			Napier.e("Failed to load Server Settings.", e)
			null
		}
	}

	override fun storeServerSettings(settings: PersistedServerSettings, projectsDir: HPath) {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		fileSystem.createDirectories(path.parent!!)
		fileSystem.writeJson(path, json, settings)
	}

	override fun removeServerSettings(projectsDir: HPath): PersistedServerSettings? {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		val account = fileSystem.readJsonOrNull<PersistedServerSettings>(path, json)
		fileSystem.delete(path)
		return account
	}

	private fun getServerSettingsPath(projectsDir: HPath): HPath {
		return (projectsDir.toOkioPath() / SERVER_FILE_NAME).toHPath()
	}

	companion object {
		const val SERVER_FILE_NAME = "server.json"
	}
}
