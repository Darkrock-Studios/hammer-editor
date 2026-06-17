package com.darkrockstudios.apps.hammer.encryption

class ContentEncryptorRegistry(
	encryptors: List<ContentEncryptor>,
) {
	private val byTag: Map<String, ContentEncryptor> = encryptors.associateBy { it.cipherName() }

	/**
	 * Resolves the encryptor for a row's `cipher` tag. NULL means plaintext;
	 * an unknown non-null tag is a hard error.
	 */
	fun resolve(cipher: String?): ContentEncryptor {
		val tag = cipher ?: PlaintextContentEncryptor.CIPHER_NAME
		return byTag[tag] ?: error("No ContentEncryptor registered for cipher tag '$tag'")
	}
}
