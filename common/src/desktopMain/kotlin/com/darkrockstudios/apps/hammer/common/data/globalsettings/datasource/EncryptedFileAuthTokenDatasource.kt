package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource.Companion.accountKey
import com.darkrockstudios.apps.hammer.common.data.secrets.MachineBoundCipher
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * AES-GCM encrypted [AuthTokenDatasource] for desktop. The account-keyed token map is
 * serialized to JSON, encrypted, and written to the app-private config directory.
 *
 * See [MachineBoundCipher] for the key and the threat model.
 */
class EncryptedFileAuthTokenDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val filePath: Path = DEFAULT_FILE_PATH,
	keyUserName: String = System.getProperty("user.name").orEmpty(),
	keyHomeDir: String = System.getProperty("user.home").orEmpty(),
	keySalt: ByteArray = APP_SALT,
) : AuthTokenDatasource {

	private val lock = reentrantLock()
	private val cipher = MachineBoundCipher(keyUserName, keyHomeDir, keySalt)

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
		if (!fileSystem.exists(filePath)) return emptyMap()
		return try {
			val encrypted = fileSystem.read(filePath) { readByteArray() }
			json.decodeFromString<Map<String, AuthTokens>>(cipher.decrypt(encrypted).decodeToString())
			// Wrong machine/user, corruption, or tampering: treat as no tokens (forces re-login).
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to decrypt auth token store; treating as empty", e)
			emptyMap()
		}
	}

	private fun store(tokens: Map<String, AuthTokens>) {
		fileSystem.createDirectories(filePath.parent!!)
		val plaintext = json.encodeToString(tokens).encodeToByteArray()
		fileSystem.write(filePath) { write(cipher.encrypt(plaintext)) }
		MachineBoundCipher.restrictToOwner(filePath)
	}

	companion object {
		private const val FILE_NAME = "auth_tokens.enc"
		private val APP_SALT = "hammer-editor::auth-token-store::v1".encodeToByteArray()

		val DEFAULT_FILE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
