package com.darkrockstudios.apps.hammer.encryption

import io.ktor.utils.io.core.toByteArray
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

class AesGcmContentEncryptor(
	private val aesKeyProvider: AesGcmKeyProvider,
	private val random: SecureRandom,
) : ContentEncryptor {

	override suspend fun encrypt(plainText: String, clientSecret: String): String {
		val secretKey = aesKeyProvider.getEncryptionKey(clientSecret)
		val cipher = Cipher.getInstance(CIPHER_NAME)
		val iv = ByteArray(IV_LENGTH) // Generate a 96-bit IV
		random.nextBytes(iv)
		val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
		val encryptedBytes = cipher.doFinal(plainText.toByteArray())
		val combinedBytes = iv + encryptedBytes
		val encryptedText = Base64.encode(combinedBytes)
		return encryptedText
	}

	override suspend fun decrypt(encrypted: String, clientSecret: String): String {
		val encryptedBytes = Base64.decode(encrypted.toByteArray())
		val secretKey = aesKeyProvider.getEncryptionKey(clientSecret)
		val cipher = Cipher.getInstance(CIPHER_NAME)
		val iv = encryptedBytes.sliceArray(0..<IV_LENGTH)
		val ciphertext = encryptedBytes.sliceArray(IV_LENGTH until encryptedBytes.size)
		val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
		val plainTextBytes = cipher.doFinal(ciphertext)
		return plainTextBytes.toString(Charsets.UTF_8)
	}

	override fun cipherName() = CIPHER_NAME

	companion object {
		const val CIPHER_NAME: String = "AES/GCM/NoPadding"
		const val TAG_LENGTH: Int = 128
		const val IV_LENGTH: Int = 12
	}
}
