package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.base.http.readJsonOrNull
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem

class ServerSettingsFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val authTokenStore: AuthTokenStore,
) : ServerSettingsDatasource {

	override fun serverIsSetup(projectsDir: HPath): Boolean {
		return fileSystem.exists(getServerSettingsPath(projectsDir).toOkioPath())
	}

	override fun loadServerSettings(projectsDir: HPath): ServerSettings? {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		Napier.i { "Loading Server Settings from: $path" }

		if (!fileSystem.exists(path)) return null

		val settingsText: String = fileSystem.read(path) { readUtf8() }

		val persisted: PersistedServerSettings = try {
			json.decodeFromString(settingsText)
		} catch (e: SerializationException) {
			Napier.e("Failed to load Server Settings.", e)
			return null
		}

		val tokens = authTokenStore.get(persisted.url, persisted.userId)
		return persisted.toServerSettings(tokens)
	}

	override fun storeServerSettings(settings: ServerSettings, projectsDir: HPath) {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		fileSystem.createDirectories(path.parent!!)
		fileSystem.writeJson(path, json, settings.toPersisted())

		authTokenStore.put(
			settings.url,
			settings.userId,
			AuthTokens(settings.bearerToken, settings.refreshToken),
		)
	}

	override fun removeServerSettings(projectsDir: HPath) {
		val path = getServerSettingsPath(projectsDir).toOkioPath()

		val account = fileSystem.readJsonOrNull<PersistedServerSettings>(path, json)

		fileSystem.delete(path)
		account?.let { authTokenStore.remove(it.url, it.userId) }
	}

	/**
	 * Moves inline tokens from a legacy `server.json` into the token store and rewrites
	 * the file tokenless, so the plaintext secrets no longer live in the workspace.
	 * Idempotent and self-gating: a no-op once the file carries no inline tokens.
	 *
	 * Transitional: delete this (and [readInlineTokens]) once 4.0 ships and every upgrading
	 * user has run it.
	 */
	override fun migrateInlineTokens(projectsDir: HPath) {
		val path = getServerSettingsPath(projectsDir).toOkioPath()
		if (!fileSystem.exists(path)) return

		val settingsText = fileSystem.read(path) { readUtf8() }
		val inline = readInlineTokens(settingsText) ?: return

		val persisted: PersistedServerSettings = try {
			json.decodeFromString(settingsText)
		} catch (e: SerializationException) {
			Napier.e("Failed to parse server.json during inline-token migration.", e)
			return
		}

		try {
			authTokenStore.put(persisted.url, persisted.userId, inline)
			fileSystem.writeJson(path, json, persisted)
			Napier.i("Migrated inline server.json tokens into the token store")
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to migrate inline auth tokens", e)
		}
	}

	private fun readInlineTokens(settingsText: String): AuthTokens? {
		val obj = runCatching { json.parseToJsonElement(settingsText).jsonObject }.getOrNull() ?: return null
		val bearer = obj["bearerToken"]?.jsonPrimitive?.contentOrNull
		val refresh = obj["refreshToken"]?.jsonPrimitive?.contentOrNull
		return if (bearer != null || refresh != null) {
			AuthTokens(bearer, refresh)
		} else {
			null
		}
	}

	private fun getServerSettingsPath(projectsDir: HPath): HPath {
		return (projectsDir.toOkioPath() / SERVER_FILE_NAME).toHPath()
	}

	companion object {
		const val SERVER_FILE_NAME = "server.json"
	}
}
