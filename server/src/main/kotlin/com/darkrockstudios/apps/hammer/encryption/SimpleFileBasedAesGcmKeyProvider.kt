package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * This generates an AES key for each account as requested.
 */
class SimpleFileBasedAesGcmKeyProvider(
	private val keyringManager: KeyringManager,
	private val base64: Base64,
) : AesGcmKeyProvider {
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

	override suspend fun getEncryptionKey(clientSecret: String): SecretKey {
		val cachedKey = cache.get(clientSecret)
		if (cachedKey != null) {
			return cachedKey
		}

		val serverSecret = keyringManager.activeContentKey()

		val derivedKey = deriveAesKey(serverSecret, clientSecret, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
		cache.put(clientSecret, derivedKey)
		return derivedKey
	}

	companion object {
		private const val PBKDF2_ITERATIONS = 65536
		private const val PBKDF2_KEY_LENGTH = 256
	}
}

class KeyLoadingException(message: String, cause: Throwable) : Exception(message, cause)