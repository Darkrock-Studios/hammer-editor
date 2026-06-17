package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utils.BaseTest
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
	}

	private fun keyProviderWith(contentKey: String): SimpleFileBasedAesGcmKeyProvider =
		SimpleFileBasedAesGcmKeyProvider(managerWith(contentKey), base64)

	private fun managerWith(contentKey: String): KeyringManager {
		val keyring = Keyring(
			content = RoleKeys("v1", mapOf("v1" to contentKey)),
			tokenHmac = RoleKeys("v1", mapOf("v1" to contentKey)),
		)
		val codec = KeyringCodec(secureRandom, base64)
		val json = codec.serialize(keyring)
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String = json
		}
		return KeyringManager(provider, codec, fileSystem, "/nonexistent".toPath())
	}

	@Test
	fun `generates a 256-bit AES key for a client secret`() = runTest {
		val key = keyProviderWith("server-key").getEncryptionKey(clientSecret1)
		assertNotNull(key)
		assertEquals("AES", key.algorithm)
		assertEquals(32, key.encoded.size)
	}

	@Test
	fun `same client secret returns the cached key`() = runTest {
		val provider = keyProviderWith("server-key")
		val first = provider.getEncryptionKey(clientSecret1)
		val second = provider.getEncryptionKey(clientSecret1)
		assertTrue(first.encoded.contentEquals(second.encoded))
	}

	@Test
	fun `different client secrets produce different keys`() = runTest {
		val provider = keyProviderWith("server-key")
		val key1 = provider.getEncryptionKey(clientSecret1)
		val key2 = provider.getEncryptionKey(clientSecret2)
		assertNotEquals(key1, key2)
		assertTrue(key1.encoded.contentEquals(key2.encoded).not())
	}

	@Test
	fun `key derivation is deterministic across provider instances with the same content key`() = runTest {
		val first = keyProviderWith("server-key").getEncryptionKey(clientSecret1)
		val second = keyProviderWith("server-key").getEncryptionKey(clientSecret1)
		assertTrue(first.encoded.contentEquals(second.encoded))
	}

	@Test
	fun `different content keys produce different derived keys`() = runTest {
		val first = keyProviderWith("server-key-A").getEncryptionKey(clientSecret1)
		val second = keyProviderWith("server-key-B").getEncryptionKey(clientSecret1)
		assertTrue(first.encoded.contentEquals(second.encoded).not())
	}

	@Test
	fun `cache survives eviction beyond ten client secrets`() = runTest {
		val provider = keyProviderWith("server-key")
		val clientSecrets = (0..14).map { i -> Base64.encode(ByteArray(32) { (it + i).toByte() }) }
		val keys = clientSecrets.map { provider.getEncryptionKey(it) }

		clientSecrets.take(5).forEachIndexed { index, secret ->
			val reaccessed = provider.getEncryptionKey(secret)
			assertTrue(keys[index].encoded.contentEquals(reaccessed.encoded))
		}
	}

	@Test
	fun `keys for several client secrets stay stable`() = runTest {
		val provider = keyProviderWith("server-key")
		val a1 = provider.getEncryptionKey(clientSecret1)
		val b1 = provider.getEncryptionKey(clientSecret2)
		val c1 = provider.getEncryptionKey(clientSecret3)

		assertTrue(a1.encoded.contentEquals(provider.getEncryptionKey(clientSecret1).encoded))
		assertTrue(b1.encoded.contentEquals(provider.getEncryptionKey(clientSecret2).encoded))
		assertTrue(c1.encoded.contentEquals(provider.getEncryptionKey(clientSecret3).encoded))
	}
}
