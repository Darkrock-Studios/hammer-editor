package com.darkrockstudios.apps.hammer.encryption

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ContentEncryptorRegistryTest {

	private val keyProvider = SimpleFileBasedAesGcmKeyProvider(Base64.Default)
	private val random = SecureRandom()

	private fun aes(keyId: String) = AesGcmContentEncryptor("content-$keyId", keyId, keyProvider, random)
	private val plaintext = PlaintextContentEncryptor()

	@Test
	fun `resolves a key-id tag to its encryptor`() {
		val v1 = aes("v1")
		val v2 = aes("v2")
		val registry = ContentEncryptorRegistry(listOf(v1, v2, plaintext))

		assertSame(v1, registry.resolve("aesgcm:v1"))
		assertSame(v2, registry.resolve("aesgcm:v2"))
	}

	@Test
	fun `legacy tag resolves to the v1 encryptor`() {
		val v1 = aes("v1")
		val registry = ContentEncryptorRegistry(listOf(v1, plaintext))

		assertSame(v1, registry.resolve("AES/GCM/NoPadding"))
	}

	@Test
	fun `null and none resolve to plaintext`() {
		val registry = ContentEncryptorRegistry(listOf(aes("v1"), plaintext))

		assertSame(plaintext, registry.resolve(null))
		assertSame(plaintext, registry.resolve("none"))
	}

	@Test
	fun `unknown tag is a hard error`() {
		val registry = ContentEncryptorRegistry(listOf(aes("v1"), plaintext))

		assertFailsWith<IllegalStateException> { registry.resolve("aesgcm:v9") }
		assertFailsWith<IllegalStateException> { registry.resolve("bogus") }
	}

	@Test
	fun `legacy alias is absent when there is no v1 key`() {
		// After rotating off v1 and dropping it, legacy rows should already be gone;
		// the alias simply isn't registered.
		val registry = ContentEncryptorRegistry(listOf(aes("v2"), plaintext))

		assertFailsWith<IllegalStateException> { registry.resolve("AES/GCM/NoPadding") }
	}
}
