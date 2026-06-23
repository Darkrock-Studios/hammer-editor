package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.secret.KeyringManager

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

	/**
	 * The encryptor new writes use for [mode]. The single source of this decision —
	 * both the DI binding and the convergence gate resolve the active encryptor here,
	 * so the write target and the convergence target can't drift apart.
	 */
	fun active(mode: EncryptionMode, keyringManager: KeyringManager): ContentEncryptor =
		when (mode) {
			EncryptionMode.NONE -> plaintext
			EncryptionMode.AES -> {
				val activeId = keyringManager.activeContentKeyId()
				aesByKeyId[activeId] ?: error("No content encryptor for active key '$activeId'")
			}
		}
}
