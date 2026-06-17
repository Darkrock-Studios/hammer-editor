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
	}
}
