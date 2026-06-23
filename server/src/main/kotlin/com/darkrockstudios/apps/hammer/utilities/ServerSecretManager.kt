package com.darkrockstudios.apps.hammer.utilities

import okio.FileSystem
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Manages the auto-generated `server.secret` file — the token-HMAC key fallback
 * used when no keyring is configured. Created on first access, then cached.
 */
@OptIn(ExperimentalEncodingApi::class)
class ServerSecretManager(
	private val fileSystem: FileSystem,
	private val secureRandom: SecureRandom,
) {
	private val SERVER_KEY_FILE_NAME = "server.secret"
	private val SERVER_SECRET_ENTROPY_BYTES = 32

	private val secretPath = getRootDataDirectory(fileSystem) / SERVER_KEY_FILE_NAME

	private var cachedServerSecret: String? = null

	/**
	 * Get the server secret as a UTF-8 string.
	 * If the secret doesn't exist, it will be created automatically.
	 * The secret is cached after first load.
	 */
	suspend fun getServerSecret(): String {
		// Return cached if available
		cachedServerSecret?.let { return it }

		// Try to load from disk
		val loaded = loadFromDisk()
		if (loaded != null) {
			cachedServerSecret = loaded
			return loaded
		}

		// Generate new secret
		val newSecret = generateSecret()
		saveToDisk(newSecret)
		cachedServerSecret = newSecret
		return newSecret
	}

	/**
	 * Get the server secret as a byte array (UTF-8 encoded).
	 */
	suspend fun getServerSecretBytes(): ByteArray {
		return getServerSecret().toByteArray(Charsets.UTF_8)
	}

	private fun generateSecret(): String {
		val secretBytes = ByteArray(SERVER_SECRET_ENTROPY_BYTES)
		secureRandom.nextBytes(secretBytes)
		return Base64.encode(secretBytes)
	}

	private suspend fun loadFromDisk(): String? {
		return if (fileSystem.exists(secretPath)) {
			fileSystem.read(secretPath) { readUtf8() }
		} else {
			null
		}
	}

	private suspend fun saveToDisk(secret: String) {
		val parent = secretPath.parent ?: error("Secret path has no parent directory")
		fileSystem.createDirectories(parent)

		fileSystem.write(secretPath) {
			writeUtf8(secret)
		}
	}
}
