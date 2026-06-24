package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.base.http.readJsonOrNull
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore.Companion.accountKey
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Plaintext JSON-backed [AuthTokenStore] living in the app-private config directory.
 *
 * The whole account-keyed map is persisted to a single file. A missing or corrupt
 * file is tolerated by starting from an empty map.
 *
 * The [AuthTokenStore] seam lets an encrypted backing be substituted without
 * touching its callers.
 */
class FileAuthTokenStore(
	private val fileSystem: FileSystem,
	private val json: Json,
) : AuthTokenStore {

	private val lock = reentrantLock()

	override fun get(url: String, userId: Long): AuthTokens? = lock.withLock {
		load()[accountKey(url, userId)]
	}

	override fun put(url: String, userId: Long, tokens: AuthTokens): Unit = lock.withLock {
		val updated = load().toMutableMap()
		updated[accountKey(url, userId)] = tokens
		store(updated)
	}

	override fun remove(url: String, userId: Long): Unit = lock.withLock {
		val updated = load().toMutableMap()
		if (updated.remove(accountKey(url, userId)) != null) {
			store(updated)
		}
	}

	private fun load(): Map<String, AuthTokens> {
		return fileSystem.readJsonOrNull<Map<String, AuthTokens>>(FILE_PATH, json) ?: emptyMap()
	}

	private fun store(tokens: Map<String, AuthTokens>) {
		fileSystem.createDirectories(FILE_PATH.parent!!)
		fileSystem.writeJson(FILE_PATH, json, tokens)
	}

	companion object {
		private const val FILE_NAME = "auth_tokens.json"
		val FILE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
