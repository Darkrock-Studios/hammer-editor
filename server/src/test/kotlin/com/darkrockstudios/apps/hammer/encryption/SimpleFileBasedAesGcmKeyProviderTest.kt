package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class SimpleFileBasedAesGcmKeyProviderTest : BaseTest() {

	private lateinit var base64: Base64
	private lateinit var keyProvider: SimpleFileBasedAesGcmKeyProvider

	private val contentKey = "server-content-key"
	private val clientSecret1 = Base64.encode(ByteArray(32) { it.toByte() })
	private val clientSecret2 = Base64.encode(ByteArray(32) { (it + 1).toByte() })
	private val clientSecret3 = Base64.encode(ByteArray(32) { (it + 2).toByte() })

	@BeforeEach
	override fun setup() {
		super.setup()
		base64 = Base64.Default
		setupKoin()
		keyProvider = SimpleFileBasedAesGcmKeyProvider(base64)
	}

	@Test
	fun `generates a 256-bit AES key for a client secret`() = runTest {
		val key = keyProvider.getEncryptionKey(clientSecret1, contentKey)
		assertNotNull(key)
		assertEquals("AES", key.algorithm)
		assertEquals(32, key.encoded.size)
	}

	@Test
	fun `same inputs return the cached key`() = runTest {
		val first = keyProvider.getEncryptionKey(clientSecret1, contentKey)
		val second = keyProvider.getEncryptionKey(clientSecret1, contentKey)
		assertTrue(first.encoded.contentEquals(second.encoded))
	}

	@Test
	fun `different client secrets produce different keys`() = runTest {
		val key1 = keyProvider.getEncryptionKey(clientSecret1, contentKey)
		val key2 = keyProvider.getEncryptionKey(clientSecret2, contentKey)
		assertNotEquals(key1, key2)
		assertTrue(key1.encoded.contentEquals(key2.encoded).not())
	}

	@Test
	fun `different content keys produce different keys for the same client secret`() = runTest {
		val key1 = keyProvider.getEncryptionKey(clientSecret1, "content-key-A")
		val key2 = keyProvider.getEncryptionKey(clientSecret1, "content-key-B")
		assertTrue(key1.encoded.contentEquals(key2.encoded).not())
	}

	@Test
	fun `derivation is deterministic across provider instances`() = runTest {
		val first = keyProvider.getEncryptionKey(clientSecret1, contentKey)
		val second = SimpleFileBasedAesGcmKeyProvider(base64).getEncryptionKey(clientSecret1, contentKey)
		assertTrue(first.encoded.contentEquals(second.encoded))
	}

	@Test
	fun `cache survives eviction beyond ten client secrets`() = runTest {
		val clientSecrets = (0..14).map { i -> Base64.encode(ByteArray(32) { (it + i).toByte() }) }
		val keys = clientSecrets.map { keyProvider.getEncryptionKey(it, contentKey) }

		clientSecrets.take(5).forEachIndexed { index, secret ->
			val reaccessed = keyProvider.getEncryptionKey(secret, contentKey)
			assertTrue(keys[index].encoded.contentEquals(reaccessed.encoded))
		}
	}
}
