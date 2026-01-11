package com.darkrockstudios.apps.hammer.utilities

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * Provides secure hashing for authentication tokens using HMAC-SHA256.
 * Uses the server secret as the HMAC key to prevent rainbow table attacks.
 */
class TokenHasher(
	private val serverSecretManager: ServerSecretManager,
	private val base64: Base64,
) {
	/**
	 * Hash a token using HMAC-SHA256 with the server secret.
	 */
	suspend fun hashToken(token: String): String {
		val serverSecret = serverSecretManager.getServerSecretBytes()
		val mac = Mac.getInstance("HmacSHA256")
		val secretKey = SecretKeySpec(serverSecret, "HmacSHA256")
		mac.init(secretKey)
		val hashBytes = mac.doFinal(token.toByteArray(Charsets.UTF_8))
		return base64.encode(hashBytes)
	}
}
