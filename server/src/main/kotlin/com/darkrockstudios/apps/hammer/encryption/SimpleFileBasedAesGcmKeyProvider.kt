package com.darkrockstudios.apps.hammer.encryption

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * Derives a per-user AES key from a content key (mixed with the user's client
 * secret) via PBKDF2. Cached by (content key, client secret) so rows on different
 * key generations derive independently.
 */
class SimpleFileBasedAesGcmKeyProvider(
	private val base64: Base64,
) : AesGcmKeyProvider {
	private val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
	// Keyed by (content key, client secret); convergence/rotation makes a user touch
	// one entry per key generation, so keep enough headroom to avoid re-deriving.
	private val cache = InMemoryKache<String, SecretKey>(maxSize = 100) {
		strategy = KacheStrategy.LRU
	}

	private fun deriveAesKey(
		contentKey: String,
		clientSecret: String,
		iterations: Int,
		keyLength: Int
	): SecretKey {
		val clientSecretBytes = base64.decode(clientSecret)
		val spec = PBEKeySpec(contentKey.toCharArray(), clientSecretBytes, iterations, keyLength)
		return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
	}

	override suspend fun getEncryptionKey(clientSecret: String, contentKey: String): SecretKey {
		// Length-prefix the content key so an arbitrary grandfathered value can't
		// forge a boundary and collide with another (contentKey, clientSecret) pair.
		val cacheKey = "${contentKey.length}:$contentKey:$clientSecret"
		cache.get(cacheKey)?.let { return it }

		val derivedKey = deriveAesKey(contentKey, clientSecret, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
		cache.put(cacheKey, derivedKey)
		return derivedKey
	}

	companion object {
		private const val PBKDF2_ITERATIONS = 65536
		private const val PBKDF2_KEY_LENGTH = 256
	}
}

class KeyLoadingException(message: String, cause: Throwable) : Exception(message, cause)
