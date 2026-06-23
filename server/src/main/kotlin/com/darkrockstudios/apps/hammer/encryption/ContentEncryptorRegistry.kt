package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.secret.KeyringCodec

class ContentEncryptorRegistry(
	encryptors: List<ContentEncryptor>,
) {
	private val byTag: Map<String, ContentEncryptor> = buildMap {
		encryptors.forEach { encryptor ->
			require(encryptor.cipherName() !in this) {
				"Duplicate cipher tag '${encryptor.cipherName()}' registered"
			}
			put(encryptor.cipherName(), encryptor)
		}
		// Rows written before key ids carry the legacy tag; they used the v1 content key.
		get("${AesGcmContentEncryptor.ALGORITHM}:${KeyringCodec.FIRST_KEY_ID}")?.let {
			put(AesGcmContentEncryptor.LEGACY_CIPHER_NAME, it)
		}
	}

	/**
	 * Resolves the encryptor a row was written with. A NULL `cipher` is plaintext;
	 * an unknown non-null tag is a hard error — never fall back, that would read
	 * ciphertext as garbage.
	 */
	fun resolve(cipher: String?): ContentEncryptor {
		val tag = cipher ?: PlaintextContentEncryptor.CIPHER_NAME
		return byTag[tag] ?: error("No ContentEncryptor registered for cipher tag '$tag'")
	}
}
