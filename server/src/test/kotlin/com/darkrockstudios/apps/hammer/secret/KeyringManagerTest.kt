package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KeyringManagerTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var secureRandom: SecureRandom
	private lateinit var codec: KeyringCodec
	private lateinit var legacyPath: Path

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()
		secureRandom = SecureRandom()
		codec = KeyringCodec(secureRandom, Base64.Default)
		legacyPath = KeyringManager.legacySecretPath()
	}

	private fun manager(provider: ServerSecretProvider) =
		KeyringManager(provider, codec, fileSystem, legacyPath)

	private fun writeLegacy(value: String) {
		fileSystem.createDirectories(legacyPath.parent!!)
		fileSystem.write(legacyPath) { writeUtf8(value) }
	}

	@Test
	fun `prefers a keyring from the provider over the legacy file`() {
		writeLegacy("legacy")
		val keyring = codec.generate()
		val provider = object : ServerSecretProvider {
			override fun loadKeyring() = codec.serialize(keyring)
		}

		assertEquals(keyring.content.activeKey(), manager(provider).activeContentKey())
	}

	@Test
	fun `grandfathers a pre-existing legacy secret when no keyring is present`() {
		val legacy = "plain-legacy-secret"
		writeLegacy(legacy)
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}

		val mgr = manager(provider)
		assertEquals(legacy, mgr.activeContentKey())
		assertEquals(legacy, mgr.keyringOrNull()!!.tokenHmac.activeKey())
	}

	@Test
	fun `grandfather preserves a non-UTF8-clean legacy secret exactly`() = runTest {
		// A secret that survived the old lossy generation: replacement char, multibyte,
		// and a control byte. Re-encoding any of these would shift the derived key.
		val trickySecret = "sec�ret-é中-tail"
		writeLegacy(trickySecret)
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}

		val grandfathered = manager(provider).activeContentKey()

		// Byte-for-byte identical to what the legacy secret reader produces.
		val legacyRead = ServerSecretManager(fileSystem, secureRandom).getServerSecret()
		assertEquals(trickySecret, grandfathered)
		assertEquals(legacyRead, grandfathered)
	}

	@Test
	fun `no keyring and no legacy secret yields null`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}
		assertNull(manager(provider).keyringOrNull())
	}

	@Test
	fun `requireContentKey fails fast when nothing is available`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String? = null
		}
		assertFailsWith<MissingKeyringException> { manager(provider).requireContentKey() }
	}

	@Test
	fun `a malformed keyring fails with a clear error, not a raw parse exception`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String = "{ this is not valid json"
		}
		assertFailsWith<MalformedKeyringException> { manager(provider).keyringOrNull() }
	}

	@Test
	fun `an invalid keyring (active id missing) fails with a clear error`() {
		val provider = object : ServerSecretProvider {
			override fun loadKeyring(): String =
				"""{"schema":1,"content":{"active":"v9","keys":{"v1":"a"}},"tokenHmac":{"active":"v1","keys":{"v1":"a"}}}"""
		}
		assertFailsWith<MalformedKeyringException> { manager(provider).keyringOrNull() }
	}
}
