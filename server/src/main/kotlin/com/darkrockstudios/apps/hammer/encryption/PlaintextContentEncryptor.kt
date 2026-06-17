package com.darkrockstudios.apps.hammer.encryption

class PlaintextContentEncryptor : ContentEncryptor {
	override suspend fun encrypt(plainText: String, clientSecret: String): String = plainText
	override suspend fun decrypt(encrypted: String, clientSecret: String): String = encrypted
	override fun cipherName() = CIPHER_NAME

	companion object {
		const val CIPHER_NAME: String = "none"
	}
}
