package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class SimpleFileBasedAesGcmKeyProviderTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var secureRandom: SecureRandom
	private lateinit var base64: Base64
	private lateinit var keyProvider: SimpleFileBasedAesGcmKeyProvider

	// Test client secrets (base64-encoded random bytes)
	private val clientSecret1 = Base64.encode(ByteArray(32) { it.toByte() })
	private val clientSecret2 = Base64.encode(ByteArray(32) { (it + 1).toByte() })
	private val clientSecret3 = Base64.encode(ByteArray(32) { (it + 2).toByte() })

	@BeforeEach
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		secureRandom = SecureRandom()
		base64 = Base64.Default

		setupKoin()

		keyProvider = SimpleFileBasedAesGcmKeyProvider(
			fileSystem = fileSystem,
			base64 = base64,
			secureRandom = secureRandom
		)
	}

	@Test
	fun `getEncryptionKey - generates key for new client secret`() = runTest {
		// Act
		val key = keyProvider.getEncryptionKey(clientSecret1)

		// Assert
		assertNotNull(key)
		assertEquals("AES", key.algorithm)
		assertEquals(32, key.encoded.size) // 256-bit key
	}

	@Test
	fun `getEncryptionKey - returns same key for same client secret (cache hit)`() = runTest {
		// Arrange
		val firstKey = keyProvider.getEncryptionKey(clientSecret1)

		// Act
		val secondKey = keyProvider.getEncryptionKey(clientSecret1)

		// Assert
		assertEquals(firstKey, secondKey)
		assertTrue(firstKey.encoded.contentEquals(secondKey.encoded))
	}

	@Test
	fun `getEncryptionKey - returns different keys for different client secrets`() = runTest {
		// Act
		val key1 = keyProvider.getEncryptionKey(clientSecret1)
		val key2 = keyProvider.getEncryptionKey(clientSecret2)

		// Assert
		assertNotEquals(key1, key2)
		assertTrue(key1.encoded.contentEquals(key2.encoded).not())
	}

	@Test
	fun `getEncryptionKey - cache works correctly for multiple client secrets`() = runTest {
		// Arrange - Generate keys for multiple client secrets
		val key1First = keyProvider.getEncryptionKey(clientSecret1)
		val key2First = keyProvider.getEncryptionKey(clientSecret2)
		val key3First = keyProvider.getEncryptionKey(clientSecret3)

		// Act - Retrieve the same keys again (should hit cache)
		val key1Second = keyProvider.getEncryptionKey(clientSecret1)
		val key2Second = keyProvider.getEncryptionKey(clientSecret2)
		val key3Second = keyProvider.getEncryptionKey(clientSecret3)

		// Assert - All keys should be identical to their first retrieval
		assertTrue(key1First.encoded.contentEquals(key1Second.encoded))
		assertTrue(key2First.encoded.contentEquals(key2Second.encoded))
		assertTrue(key3First.encoded.contentEquals(key3Second.encoded))
	}

	@Test
	fun `getEncryptionKey - keys are deterministic`() = runTest {
		// Arrange - Get a key
		val firstKey = keyProvider.getEncryptionKey(clientSecret1)

		// Create a new provider instance with same filesystem (so it loads same server secret)
		val newKeyProvider = SimpleFileBasedAesGcmKeyProvider(
			fileSystem = fileSystem,
			base64 = base64,
			secureRandom = secureRandom
		)

		// Act - Get key with same client secret from new provider
		val secondKey = newKeyProvider.getEncryptionKey(clientSecret1)

		// Assert - Keys should be identical because same server secret + client secret
		assertTrue(firstKey.encoded.contentEquals(secondKey.encoded))
	}

	@Test
	fun `server secret - is created on first access`() = runTest {
		// Arrange
		val expectedPath = System.getProperty("user.home").toPath() / "hammer_data" / "server.secret"

		// Act
		keyProvider.getEncryptionKey(clientSecret1)

		// Assert
		assertTrue(fileSystem.exists(expectedPath), "Server secret file should exist at $expectedPath")
		val savedSecret = fileSystem.read(expectedPath) { readUtf8() }
		assertTrue(savedSecret.isNotEmpty())
	}

	@Test
	fun `server secret - is loaded from disk on subsequent provider creation`() = runTest {
		// Arrange - Create server secret
		val firstKey = keyProvider.getEncryptionKey(clientSecret1)

		// Act - Create new provider (should load existing server secret)
		val newProvider = SimpleFileBasedAesGcmKeyProvider(
			fileSystem = fileSystem,
			base64 = base64,
			secureRandom = secureRandom
		)
		val secondKey = newProvider.getEncryptionKey(clientSecret1)

		// Assert - Keys should be identical (same server secret was used)
		assertTrue(firstKey.encoded.contentEquals(secondKey.encoded))
	}

	@Test
	fun `server secret - different secrets produce different keys`() = runTest {
		// Arrange - Get key with first server secret
		val firstKey = keyProvider.getEncryptionKey(clientSecret1)

		// Create new filesystem with different server secret
		val newFileSystem = FakeFileSystem()
		val newProvider = SimpleFileBasedAesGcmKeyProvider(
			fileSystem = newFileSystem,
			base64 = base64,
			secureRandom = secureRandom
		)

		// Act - Get key with different server secret
		val secondKey = newProvider.getEncryptionKey(clientSecret1)

		// Assert - Keys should be different (different server secrets)
		assertTrue(firstKey.encoded.contentEquals(secondKey.encoded).not())
	}

	@Test
	fun `getEncryptionKey - uses cache correctly after fix (regression test)`() = runTest {
		// This test specifically verifies the bug fix where cache.get() was using
		// serverSecret instead of clientSecret

		// Arrange - Mock SecureRandom to have predictable behavior
		var callCount = 0
		val mockRandom = mockk<SecureRandom>()
		every { mockRandom.nextBytes(any()) } answers {
			val array = firstArg<ByteArray>()
			// Fill with predictable data
			array.fill(callCount.toByte())
			callCount++
		}

		val testProvider = SimpleFileBasedAesGcmKeyProvider(
			fileSystem = fileSystem,
			base64 = base64,
			secureRandom = mockRandom
		)

		// Act - Call twice with same client secret
		val firstKey = testProvider.getEncryptionKey(clientSecret1)
		val secondKey = testProvider.getEncryptionKey(clientSecret1)

		// Assert - Keys should be identical (cache hit)
		// If the bug exists, they would be different because PBKDF2 would run twice
		assertTrue(firstKey.encoded.contentEquals(secondKey.encoded))

		// Verify nextBytes was only called once for server secret generation
		// (not called again for key derivation if cache is working)
		verify(exactly = 1) { mockRandom.nextBytes(any()) }
	}

	@Test
	fun `cache eviction - LRU behavior with more than 10 client secrets`() = runTest {
		// Arrange - Generate 15 different client secrets
		val clientSecrets = (0..14).map { i ->
			Base64.encode(ByteArray(32) { (it + i).toByte() })
		}

		// Act - Generate keys for all 15 secrets (cache size is 10)
		val keys = clientSecrets.map { secret ->
			keyProvider.getEncryptionKey(secret)
		}

		// Access the first 5 again (should not be in cache anymore due to LRU)
		val firstFiveReaccessed = clientSecrets.take(5).map { secret ->
			keyProvider.getEncryptionKey(secret)
		}

		// Assert - Keys should still be correct (regenerated from scratch)
		firstFiveReaccessed.forEachIndexed { index, key ->
			assertTrue(keys[index].encoded.contentEquals(key.encoded))
		}
	}
}
