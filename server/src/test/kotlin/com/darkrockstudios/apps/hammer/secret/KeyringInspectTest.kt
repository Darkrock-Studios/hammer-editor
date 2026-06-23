package com.darkrockstudios.apps.hammer.secret

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyringInspectTest {

	@Test
	fun `summary lists ids and active without revealing key bytes`() {
		val keyring = Keyring(
			content = RoleKeys("v2", mapOf("v1" to "SECRET-CONTENT-1", "v2" to "SECRET-CONTENT-2")),
			tokenHmac = RoleKeys("v1", mapOf("v1" to "SECRET-TOKEN-1")),
		)

		val summary = keyringSummary(keyring)

		assertTrue(summary.contains("content: active=v2 keys=[v1, v2]"))
		assertTrue(summary.contains("tokenHmac: active=v1 keys=[v1]"))
		assertFalse(summary.contains("SECRET-CONTENT-1"))
		assertFalse(summary.contains("SECRET-CONTENT-2"))
		assertFalse(summary.contains("SECRET-TOKEN-1"))
	}
}
