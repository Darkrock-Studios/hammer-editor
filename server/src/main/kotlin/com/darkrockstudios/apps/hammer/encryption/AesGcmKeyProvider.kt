package com.darkrockstudios.apps.hammer.encryption

import javax.crypto.SecretKey

interface AesGcmKeyProvider {
	suspend fun getEncryptionKey(clientSecret: String): SecretKey
}