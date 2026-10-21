package com.darkrockstudios.apps.hammer.common.data.secrets

import io.github.aakira.napier.Napier
import okio.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM under a key derived (PBKDF2) from the OS user name and home directory plus [salt], so no key
 * material is written to disk, and a file copied to another machine or user cannot be decrypted.
 *
 * Threat model: this protects against casual disk scraping, config-dir backup or exfiltration, and
 * off-machine copies. It does NOT defend against same-user local malware, which can re-derive the key.
 */
class MachineBoundCipher(
	userName: String = System.getProperty("user.name").orEmpty(),
	homeDir: String = System.getProperty("user.home").orEmpty(),
	salt: ByteArray,
) {
	private val secretKey: SecretKeySpec = deriveKey(userName, homeDir, salt)
	private val secureRandom = SecureRandom()

	fun encrypt(plaintext: ByteArray): ByteArray {
		val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
		return iv + cipher.doFinal(plaintext)
	}

	fun decrypt(input: ByteArray): ByteArray {
		require(input.size >= IV_LENGTH + GCM_TAG_BITS / 8) { "Ciphertext too short" }
		val iv = input.copyOfRange(0, IV_LENGTH)
		val ciphertext = input.copyOfRange(IV_LENGTH, input.size)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
		return cipher.doFinal(ciphertext)
	}

	private fun deriveKey(userName: String, homeDir: String, salt: ByteArray): SecretKeySpec {
		val spec = PBEKeySpec("$userName|$homeDir".toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
		val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
		return SecretKeySpec(keyBytes, "AES")
	}

	companion object {
		private const val TRANSFORMATION = "AES/GCM/NoPadding"
		private const val IV_LENGTH = 12
		private const val GCM_TAG_BITS = 128
		private const val KEY_LENGTH_BITS = 256
		private const val PBKDF2_ITERATIONS = 120_000

		/** Owner read and write only, where the file system has POSIX permissions. Best effort. */
		fun restrictToOwner(path: Path) {
			try {
				val nioPath = java.nio.file.Paths.get(path.toString())
				if (nioPath.fileSystem.supportedFileAttributeViews().contains("posix")) {
					java.nio.file.Files.setPosixFilePermissions(
						nioPath,
						setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
					)
				}
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.d("Could not restrict permissions of $path", e)
			}
		}
	}
}
