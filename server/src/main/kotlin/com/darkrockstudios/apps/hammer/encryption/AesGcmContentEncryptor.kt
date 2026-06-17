package com.darkrockstudios.apps.hammer.encryption

import io.ktor.utils.io.core.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/**
 * AES/GCM content encryptor bound to one content key generation. Its cipher tag
 * (`aesgcm:<keyId>`) records which generation a row was written with, so reads
 * dispatch to the right key.
 */
class AesGcmContentEncryptor(
	private val contentKey: String,
	private val keyId: String,
	private val aesKeyProvider: AesGcmKeyProvider,
	private val random: SecureRandom,
) : ContentEncryptor {

	override suspend fun encrypt(plainText: String, clientSecret: String): String {
		val secretKey = aesKeyProvider.getEncryptionKey(clientSecret, contentKey)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		val iv = ByteArray(IV_LENGTH)
		random.nextBytes(iv)
		val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
		val encryptedBytes = cipher.doFinal(plainText.toByteArray())
		val combinedBytes = iv + encryptedBytes
		return Base64.encode(combinedBytes)
	}

	override suspend fun decrypt(encrypted: String, clientSecret: String): String {
		val encryptedBytes = Base64.decode(encrypted.toByteArray())
		val secretKey = aesKeyProvider.getEncryptionKey(clientSecret, contentKey)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		val iv = encryptedBytes.sliceArray(0..<IV_LENGTH)
		val ciphertext = encryptedBytes.sliceArray(IV_LENGTH until encryptedBytes.size)
		val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
		val plainTextBytes = cipher.doFinal(ciphertext)
		return plainTextBytes.toString(Charsets.UTF_8)
	}

	override fun cipherName() = "$ALGORITHM:$keyId"

	companion object {
		const val ALGORITHM: String = "aesgcm"
		/** The cipher tag written before key ids existed; equivalent to `aesgcm:v1`. */
		const val LEGACY_CIPHER_NAME: String = "AES/GCM/NoPadding"
		const val TAG_LENGTH: Int = 128
		const val IV_LENGTH: Int = 12
		private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
	}
}
