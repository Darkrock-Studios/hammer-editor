package com.darkrockstudios.apps.hammer.encryption

/**
 * The encryptors available to a running server: the plaintext identity encryptor
 * plus one AES encryptor per content key generation in the keyring. Used to build
 * the read registry and to pick the active write encryptor.
 */
class ContentEncryptors(
	val plaintext: PlaintextContentEncryptor,
	val aesByKeyId: Map<String, AesGcmContentEncryptor>,
) {
	fun all(): List<ContentEncryptor> = aesByKeyId.values + plaintext
}
