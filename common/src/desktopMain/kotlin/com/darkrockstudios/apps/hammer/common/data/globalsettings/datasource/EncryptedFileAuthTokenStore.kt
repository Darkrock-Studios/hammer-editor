package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore.Companion.accountKey
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encrypted [AuthTokenStore] for desktop. The account-keyed token map is
 * serialized to JSON, encrypted, and written to the app-private config directory.
 *
 * The encryption key is derived (PBKDF2) from stable per-user/per-machine inputs
 * (OS user name + home directory) plus a static app salt; no key material is
 * written to disk. A token file copied to another machine or user therefore can't
 * be decrypted.
 *
 * Threat model: this protects against casual disk scraping, config-dir backup or
 * exfiltration, and off-machine copies. It does NOT defend against same-user local
 * malware, which can re-derive the key from the same inputs.
 */
class EncryptedFileAuthTokenStore(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val filePath: Path = DEFAULT_FILE_PATH,
	keyUserName: String = System.getProperty("user.name").orEmpty(),
	keyHomeDir: String = System.getProperty("user.home").orEmpty(),
	keySalt: ByteArray = APP_SALT,
) : AuthTokenStore {

	private val lock = reentrantLock()
	private val secretKey: SecretKeySpec = deriveKey(keyUserName, keyHomeDir, keySalt)
	private val secureRandom = SecureRandom()

	override fun get(url: String, userId: Long): AuthTokens? = lock.withLock {
		load()[accountKey(url, userId)]
	}

	override fun put(url: String, userId: Long, tokens: AuthTokens): Unit = lock.withLock {
		val updated = load().toMutableMap()
		updated[accountKey(url, userId)] = tokens
		store(updated)
	}

	override fun remove(url: String, userId: Long): Unit = lock.withLock {
		val updated = load().toMutableMap()
		if (updated.remove(accountKey(url, userId)) != null) {
			store(updated)
		}
	}

	private fun load(): Map<String, AuthTokens> {
		if (!fileSystem.exists(filePath)) return emptyMap()
		return try {
			val encrypted = fileSystem.read(filePath) { readByteArray() }
			json.decodeFromString<Map<String, AuthTokens>>(decrypt(encrypted).decodeToString())
			// Wrong machine/user, corruption, or tampering: treat as no tokens (forces re-login).
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to decrypt auth token store; treating as empty", e)
			emptyMap()
		}
	}

	private fun store(tokens: Map<String, AuthTokens>) {
		fileSystem.createDirectories(filePath.parent!!)
		val plaintext = json.encodeToString(tokens).encodeToByteArray()
		fileSystem.write(filePath) { write(encrypt(plaintext)) }
		restrictPermissions(filePath)
	}

	private fun encrypt(plaintext: ByteArray): ByteArray {
		val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
		val ciphertext = cipher.doFinal(plaintext)
		return iv + ciphertext
	}

	private fun decrypt(input: ByteArray): ByteArray {
		require(input.size >= IV_LENGTH + GCM_TAG_BITS / 8) { "Ciphertext too short" }
		val iv = input.copyOfRange(0, IV_LENGTH)
		val ciphertext = input.copyOfRange(IV_LENGTH, input.size)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
		return cipher.doFinal(ciphertext)
	}

	private fun restrictPermissions(path: Path) {
		try {
			val nioPath = java.nio.file.Paths.get(path.toString())
			val fs = nioPath.fileSystem
			if (fs.supportedFileAttributeViews().contains("posix")) {
				java.nio.file.Files.setPosixFilePermissions(
					nioPath,
					setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
				)
			}
			// Best-effort: unsupported (e.g. Windows) or denied perms are non-fatal.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.d("Could not restrict auth token file permissions", e)
		}
	}

	private fun deriveKey(userName: String, homeDir: String, salt: ByteArray): SecretKeySpec {
		val password = "$userName|$homeDir".toCharArray()
		val spec: KeySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
		val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
		val keyBytes = factory.generateSecret(spec).encoded
		return SecretKeySpec(keyBytes, "AES")
	}

	companion object {
		private const val FILE_NAME = "auth_tokens.enc"
		private const val TRANSFORMATION = "AES/GCM/NoPadding"
		private const val IV_LENGTH = 12
		private const val GCM_TAG_BITS = 128
		private const val KEY_LENGTH_BITS = 256
		private const val PBKDF2_ITERATIONS = 120_000
		private val APP_SALT = "hammer-editor::auth-token-store::v1".encodeToByteArray()

		val DEFAULT_FILE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
