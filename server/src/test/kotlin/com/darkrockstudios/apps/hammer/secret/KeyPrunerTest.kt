package com.darkrockstudios.apps.hammer.secret

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class KeyPrunerTest {

	private val pruner = KeyPruner()

	private fun keyring(
		content: RoleKeys,
		tokenHmac: RoleKeys = RoleKeys("v1", mapOf("v1" to "t1")),
	) = Keyring(content = content, tokenHmac = tokenHmac)

	@Test
	fun `sweep drops non-active content generations with no rows on them`() {
		val ring = keyring(content = RoleKeys("v3", mapOf("v1" to "k1", "v2" to "k2", "v3" to "k3")))

		val result = pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = setOf("v3"))

		assertEquals(setOf("v3"), result.keyring.content.keys.keys)
		assertEquals(listOf("v1", "v2"), result.pruned)
		assertEquals(emptyList(), result.keptReferenced)
	}

	@Test
	fun `sweep never drops the active generation even if no rows reference it`() {
		val ring = keyring(content = RoleKeys("v2", mapOf("v1" to "k1", "v2" to "k2")))

		// Plaintext server: nothing references any content key.
		val result = pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = emptySet())

		assertEquals(setOf("v2"), result.keyring.content.keys.keys)
		assertEquals(listOf("v1"), result.pruned)
	}

	@Test
	fun `sweep keeps a non-active generation that still has rows on it`() {
		val ring = keyring(content = RoleKeys("v2", mapOf("v1" to "k1", "v2" to "k2")))

		val result = pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = setOf("v1", "v2"))

		assertEquals(setOf("v1", "v2"), result.keyring.content.keys.keys)
		assertEquals(emptyList(), result.pruned)
		assertEquals(listOf("v1"), result.keptReferenced)
	}

	@Test
	fun `token-hmac sweep drops every non-active generation without consulting rows`() {
		val ring = keyring(
			content = RoleKeys("v1", mapOf("v1" to "k1")),
			tokenHmac = RoleKeys("v3", mapOf("v1" to "t1", "v2" to "t2", "v3" to "t3")),
		)

		val result = pruner.prune(ring, KeyRole.TOKEN_HMAC, inUseContentKeyIds = emptySet())

		assertEquals(setOf("v3"), result.keyring.tokenHmac.keys.keys)
		assertEquals(listOf("v1", "v2"), result.pruned)
		// Content role is untouched.
		assertEquals(ring.content, result.keyring.content)
	}

	@Test
	fun `targeted prune removes only the named generation`() {
		val ring = keyring(content = RoleKeys("v3", mapOf("v1" to "k1", "v2" to "k2", "v3" to "k3")))

		val result = pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = setOf("v3"), targetKey = "v1")

		assertEquals(setOf("v2", "v3"), result.keyring.content.keys.keys)
		assertEquals(listOf("v1"), result.pruned)
	}

	@Test
	fun `targeted prune fails when the named generation is active`() {
		val ring = keyring(content = RoleKeys("v2", mapOf("v1" to "k1", "v2" to "k2")))

		assertThrows<KeyPruneException> {
			pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = emptySet(), targetKey = "v2")
		}
	}

	@Test
	fun `targeted prune fails when the named generation still has rows on it`() {
		val ring = keyring(content = RoleKeys("v2", mapOf("v1" to "k1", "v2" to "k2")))

		assertThrows<KeyPruneException> {
			pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = setOf("v1", "v2"), targetKey = "v1")
		}
	}

	@Test
	fun `targeted prune fails when the named generation is absent`() {
		val ring = keyring(content = RoleKeys("v1", mapOf("v1" to "k1")))

		assertThrows<KeyPruneException> {
			pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = emptySet(), targetKey = "v9")
		}
	}

	@Test
	fun `pruned keyring remains valid`() {
		val ring = keyring(content = RoleKeys("v2", mapOf("v1" to "k1", "v2" to "k2")))

		val result = pruner.prune(ring, KeyRole.CONTENT, inUseContentKeyIds = emptySet())

		result.keyring.validate()
	}
}
