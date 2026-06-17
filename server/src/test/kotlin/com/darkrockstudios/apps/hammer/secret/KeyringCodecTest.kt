package com.darkrockstudios.apps.hammer.secret

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyringCodecTest {

	private val codec = KeyringCodec(SecureRandom(), Base64.Default)

	@Test
	fun `generate produces two roles each active at v1 with 32-byte base64 keys`() {
		val keyring = codec.generate()
		keyring.validate()

		assertEquals("v1", keyring.content.active)
		assertEquals("v1", keyring.tokenHmac.active)
		assertEquals(32, Base64.Default.decode(keyring.content.activeKey()).size)
		assertEquals(32, Base64.Default.decode(keyring.tokenHmac.activeKey()).size)
		// Roles get independent key material.
		assertTrue(keyring.content.activeKey() != keyring.tokenHmac.activeKey())
	}

	@Test
	fun `serialize then parse round-trips`() {
		val keyring = codec.generate()
		val parsed = codec.parse(codec.serialize(keyring))
		assertEquals(keyring, parsed)
	}

	@Test
	fun `grandfather wraps the legacy secret verbatim in both roles`() {
		val legacy = "legacy-secret-value"
		val keyring = codec.grandfather(legacy)

		assertEquals(legacy, keyring.content.activeKey())
		assertEquals(legacy, keyring.tokenHmac.activeKey())
		assertEquals("v1", keyring.content.active)
		assertEquals("v1", keyring.tokenHmac.active)
	}

	@Test
	fun `rotate adds a new active content key and keeps the old ones`() {
		val original = codec.generate()

		val rotated = codec.rotate(original, KeyRole.CONTENT)

		assertEquals("v2", rotated.content.active)
		assertEquals(setOf("v1", "v2"), rotated.content.keys.keys)
		// Old key value preserved so existing rows still decrypt until converged.
		assertEquals(original.content.key("v1"), rotated.content.key("v1"))
		// The other role is untouched.
		assertEquals(original.tokenHmac, rotated.tokenHmac)
	}

	@Test
	fun `rotate increments the version each time`() {
		val twice = codec.rotate(codec.rotate(codec.generate(), KeyRole.CONTENT), KeyRole.CONTENT)
		assertEquals("v3", twice.content.active)
		assertEquals(setOf("v1", "v2", "v3"), twice.content.keys.keys)
	}

	@Test
	fun `rotate ignores non-version key ids when picking the next version`() {
		val keyring = Keyring(
			content = RoleKeys("v1", mapOf("v1" to "k1", "legacy" to "k0")),
			tokenHmac = RoleKeys("v1", mapOf("v1" to "t1")),
		)
		val rotated = codec.rotate(keyring, KeyRole.CONTENT)

		assertEquals("v2", rotated.content.active)
		assertEquals(setOf("v1", "legacy", "v2"), rotated.content.keys.keys)
	}

	@Test
	fun `rotate can target the token-hmac role independently`() {
		val original = codec.generate()
		val rotated = codec.rotate(original, KeyRole.TOKEN_HMAC)

		assertEquals("v2", rotated.tokenHmac.active)
		assertEquals(original.content, rotated.content)
	}

	@Test
	fun `parse rejects an unknown schema`() {
		val json = """{"schema":99,"content":{"active":"v1","keys":{"v1":"a"}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		assertThrows<IllegalArgumentException> { codec.parse(json) }
	}

	@Test
	fun `parse rejects an active id missing from keys`() {
		val json = """{"schema":1,"content":{"active":"v2","keys":{"v1":"a"}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		assertThrows<IllegalArgumentException> { codec.parse(json) }
	}

	@Test
	fun `parse rejects an empty key set`() {
		val json = """{"schema":1,"content":{"active":"v1","keys":{}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		assertThrows<IllegalArgumentException> { codec.parse(json) }
	}

	@Test
	fun `parse rejects a blank key value`() {
		val json = """{"schema":1,"content":{"active":"v1","keys":{"v1":""}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		assertThrows<IllegalArgumentException> { codec.parse(json) }
	}
}
