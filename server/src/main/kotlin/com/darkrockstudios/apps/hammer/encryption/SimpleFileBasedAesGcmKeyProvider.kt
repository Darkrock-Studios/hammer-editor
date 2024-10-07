package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import okio.FileSystem
import okio.internal.commonToUtf8String
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.system.measureTimeMillis

/**
 * This generates an AES key for each account as requested.
 * It does not store the server secret securely.
 * It is stored as a file in the servers data directory.
 */
class SimpleFileBasedAesGcmKeyProvider(
	private val fileSystem: FileSystem,
	private val base64: Base64,
	private val secureRandom: SecureRandom,
) : AesGcmKeyProvider {
	private val SERVER_KEY_FILE_NAME = "server.secret"

	private val secretPath = getRootDataDirectory(fileSystem) / SERVER_KEY_FILE_NAME
	private var cachedServerSecret: String? = null

	private val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
	private val cache = InMemoryKache<String, SecretKey>(maxSize = 10) {
		strategy = KacheStrategy.LRU
	}

	private fun deriveAesKey(
		serverSecret: String,
		clientSecret: String,
		iterations: Int,
		keyLength: Int
	): SecretKey {
		val clientSecretBytes = base64.decode(clientSecret)
		val spec = PBEKeySpec(serverSecret.toCharArray(), clientSecretBytes, iterations, keyLength)
		return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
	}

	private suspend fun getServerSecret(): String {
		val savedSecret = loadFromDisk()
		return if (savedSecret != null) {
			savedSecret
		} else {
			val newSecretBytes = ByteArray(SERVER_SECRET_ENTROPY_BYTES)
			secureRandom.nextBytes(newSecretBytes)
			val newSecret = newSecretBytes.commonToUtf8String()
			saveToDisk(newSecret)
			newSecret
		}
	}

	private suspend fun loadFromDisk(): String? {
		return if (fileSystem.exists(secretPath)) {
			return fileSystem.read(secretPath) { readUtf8() }
		} else {
			null
		}
	}

	private suspend fun saveToDisk(serverSecret: String) {
		fileSystem.createDirectories(secretPath.parent ?: error("Parent does not exist"))
		fileSystem.write(secretPath) {
			writeUtf8(serverSecret)
		}
	}

	override suspend fun getEncryptionKey(clientSecret: String): SecretKey {
		val serverSecret = cachedServerSecret ?: getServerSecret()
		cachedServerSecret = serverSecret

		val cachedKey = cache.get(serverSecret)
		return if (cachedKey != null) {
			cachedKey
		} else {
			val derivedKey: SecretKey
			val ms = measureTimeMillis {
				derivedKey =
					deriveAesKey(serverSecret, clientSecret, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
			}
			println("------- getEncryptionKey: $ms ms")
			cache.put(clientSecret, derivedKey)
			derivedKey
		}
	}

	companion object {
		private const val SERVER_SECRET_ENTROPY_BYTES = 32
		private const val PBKDF2_ITERATIONS = 65536
		private const val PBKDF2_KEY_LENGTH = 256
	}
}

class KeyLoadingException(message: String, cause: Throwable) : Exception(message, cause)