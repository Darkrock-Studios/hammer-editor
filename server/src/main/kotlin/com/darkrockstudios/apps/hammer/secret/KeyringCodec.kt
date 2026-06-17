package com.darkrockstudios.apps.hammer.secret

import kotlinx.serialization.json.Json
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/** Parses, serializes, generates, and grandfathers keyring documents. */
class KeyringCodec(
	private val random: SecureRandom,
	private val base64: Base64,
) {
	private val parser = Json { ignoreUnknownKeys = true }
	private val writer = Json { prettyPrint = true }

	fun parse(json: String): Keyring = parser.decodeFromString(Keyring.serializer(), json).also { it.validate() }

	fun serialize(keyring: Keyring): String = writer.encodeToString(Keyring.serializer(), keyring)

	/**
	 * Adds a fresh key to a role and makes it active, keeping the old keys so
	 * existing rows still decrypt until convergence moves them onto the new key.
	 */
	fun rotate(keyring: Keyring, role: KeyRole): Keyring {
		val current = role.select(keyring)
		val newId = nextKeyId(current.keys.keys)
		val rotated = RoleKeys(active = newId, keys = current.keys + (newId to newKey()))
		return role.replace(keyring, rotated)
	}

	private fun nextKeyId(existing: Set<String>): String {
		val maxVersion = existing
			.mapNotNull { KEY_ID_PATTERN.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
			.maxOrNull() ?: 0
		return "v${maxVersion + 1}"
	}

	/** A fresh keyring with one random key per role, each active at `v1`. */
	fun generate(): Keyring = Keyring(
		content = RoleKeys(FIRST_KEY_ID, mapOf(FIRST_KEY_ID to newKey())),
		tokenHmac = RoleKeys(FIRST_KEY_ID, mapOf(FIRST_KEY_ID to newKey())),
	)

	/**
	 * Wraps a legacy single `server.secret` value as a keyring. Both roles share
	 * the exact same value at `v1` so existing content decrypts and existing
	 * tokens verify — the value must be preserved byte-for-byte, never re-encoded.
	 */
	fun grandfather(legacySecret: String): Keyring = Keyring(
		content = RoleKeys(FIRST_KEY_ID, mapOf(FIRST_KEY_ID to legacySecret)),
		tokenHmac = RoleKeys(FIRST_KEY_ID, mapOf(FIRST_KEY_ID to legacySecret)),
	)

	private fun newKey(): String {
		val bytes = ByteArray(KEY_ENTROPY_BYTES)
		random.nextBytes(bytes)
		return base64.encode(bytes)
	}

	companion object {
		const val FIRST_KEY_ID = "v1"
		const val KEY_ENTROPY_BYTES = 32
		private val KEY_ID_PATTERN = Regex("v(\\d+)")
	}
}

enum class KeyRole {
	CONTENT {
		override fun select(keyring: Keyring) = keyring.content
		override fun replace(keyring: Keyring, keys: RoleKeys) = keyring.copy(content = keys)
	},
	TOKEN_HMAC {
		override fun select(keyring: Keyring) = keyring.tokenHmac
		override fun replace(keyring: Keyring, keys: RoleKeys) = keyring.copy(tokenHmac = keys)
	};

	abstract fun select(keyring: Keyring): RoleKeys
	abstract fun replace(keyring: Keyring, keys: RoleKeys): Keyring
}
