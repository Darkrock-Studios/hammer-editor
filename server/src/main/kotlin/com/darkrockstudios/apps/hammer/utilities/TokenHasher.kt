package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.secret.KeyringManager
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * Hashes authentication tokens with HMAC-SHA256, keyed by the server secret to
 * prevent rainbow-table attacks.
 *
 * The key comes from the keyring's `tokenHmac` role when a keyring is available
 * (explicit or grandfathered). With no keyring at all — a zero-config plaintext
 * server — it falls back to the auto-managed `server.secret`, because losing this
 * key only forces a re-login, never data loss.
 */
class TokenHasher(
	private val keyringManager: KeyringManager,
	private val serverSecretManager: ServerSecretManager,
	private val base64: Base64,
) {
	suspend fun hashToken(token: String): String {
		val key = keyringManager.tokenHmacKeyOrNull() ?: serverSecretManager.getServerSecret()
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
		val hashBytes = mac.doFinal(token.toByteArray(Charsets.UTF_8))
		return base64.encode(hashBytes)
	}
}
