package com.darkrockstudios.apps.hammer.secret

import kotlinx.serialization.Serializable

/**
 * Versioned key material for the server, modeled as a single JSON document.
 *
 * Two independent roles: [content] keys protect at-rest entity content, [tokenHmac]
 * keys hash auth tokens. Each role has versioned keys and an active id.
 *
 * Key values are opaque strings consumed **directly** — as PBKDF2 password chars
 * and as UTF-8 HMAC-key bytes — never decoded back to raw bytes. New keys are
 * minted as Base64 of 32 random bytes (full-entropy ASCII); a grandfathered key
 * is the legacy `server.secret` string verbatim, so existing data stays readable.
 */
@Serializable
data class Keyring(
	val schema: Int = SCHEMA_VERSION,
	val content: RoleKeys,
	val tokenHmac: RoleKeys,
) {
	fun validate() {
		require(schema == SCHEMA_VERSION) {
			"Unsupported keyring schema $schema (this server understands $SCHEMA_VERSION)"
		}
		content.validate("content")
		tokenHmac.validate("tokenHmac")
	}

	companion object {
		const val SCHEMA_VERSION = 1
	}
}

@Serializable
data class RoleKeys(
	val active: String,
	val keys: Map<String, String>,
) {
	/** The value of the active key. */
	fun activeKey(): String = key(active)

	fun key(id: String): String =
		keys[id] ?: error("Keyring key id '$id' is not present")

	fun validate(role: String) {
		require(keys.isNotEmpty()) { "Keyring role '$role' has no keys" }
		require(active in keys) { "Keyring role '$role' active id '$active' is missing from its keys" }
		keys.forEach { (id, value) ->
			require(id.isNotBlank()) { "Keyring role '$role' has a blank key id" }
			require(value.isNotBlank()) { "Keyring role '$role' key '$id' has a blank value" }
		}
	}
}
