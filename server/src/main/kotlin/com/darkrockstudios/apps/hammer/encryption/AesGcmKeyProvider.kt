package com.darkrockstudios.apps.hammer.encryption

import javax.crypto.SecretKey

interface AesGcmKeyProvider {
	/** Derives a per-user AES key by mixing a specific content key with the user's client secret. */
	suspend fun getEncryptionKey(clientSecret: String, contentKey: String): SecretKey
}