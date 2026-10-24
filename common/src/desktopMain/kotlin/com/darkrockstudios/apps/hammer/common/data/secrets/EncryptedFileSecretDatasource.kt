package com.darkrockstudios.apps.hammer.common.data.secrets

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** [SecretDatasource] for desktop: the whole map as JSON, encrypted by a [MachineBoundCipher], in one file. */
class EncryptedFileSecretDatasource(
	private val fileSystem: FileSystem,
	private val filePath: Path = DEFAULT_FILE_PATH,
	private val cipher: MachineBoundCipher = MachineBoundCipher(salt = APP_SALT),
) : SecretDatasource {

	override fun get(key: String): String? = lock.withLock { load()[key] }

	override fun put(key: String, value: String): Unit = lock.withLock {
		val current = load()
		val updated = if (value.isEmpty()) current - key else current + (key to value)
		if (updated != current) store(updated)
	}

	override fun removeAll(prefix: String): Unit = lock.withLock {
		val current = load()
		val updated = current.filterKeys { !it.startsWith(prefix) }
		if (updated.size != current.size) store(updated)
	}

	private fun load(): Map<String, String> {
		if (!fileSystem.exists(filePath)) return emptyMap()
		return try {
			val decrypted = cipher.decrypt(fileSystem.read(filePath) { readByteArray() })
			Json.decodeFromString<Map<String, String>>(decrypted.decodeToString())
			// Wrong machine or user, corruption, or tampering: the secrets have to be entered again.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to decrypt the secret store; treating it as empty", e)
			emptyMap()
		}
	}

	private fun store(secrets: Map<String, String>) {
		fileSystem.createDirectories(filePath.parent!!)
		val staging = filePath.parent!! / "${filePath.name}.tmp"
		fileSystem.write(staging) { write(cipher.encrypt(Json.encodeToString(secrets).encodeToByteArray())) }
		MachineBoundCipher.restrictToOwner(staging)
		fileSystem.atomicMove(staging, filePath)
	}

	companion object {
		// One for every instance: the app and its plugin host each make one over the same file.
		private val lock = reentrantLock()

		private const val FILE_NAME = "secrets.enc"
		private val APP_SALT = "hammer-editor::secret-store::v1".encodeToByteArray()

		val DEFAULT_FILE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
