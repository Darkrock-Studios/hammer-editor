package com.darkrockstudios.apps.hammer.encryption

interface ContentEncryptor {
	suspend fun encrypt(plainText: String, clientSecret: String): String
	suspend fun decrypt(encrypted: String, clientSecret: String): String
	fun cipherName(): String
}