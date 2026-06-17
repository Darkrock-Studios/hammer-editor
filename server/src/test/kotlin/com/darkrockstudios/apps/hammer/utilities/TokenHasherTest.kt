package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenHasherTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var secureRandom: SecureRandom
	private lateinit var base64: Base64
	private lateinit var codec: KeyringCodec
	private val token = "a-capability-token"

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()
		secureRandom = SecureRandom()
		base64 = Base64.Default
		codec = KeyringCodec(secureRandom, base64)
	}

	private fun managerWithTokenKey(tokenKey: String, legacyPath: Path = "/none".toPath()): KeyringManager {
		val keyring = Keyring(
			content = RoleKeys("v1", mapOf("v1" to "content-key")),
			tokenHmac = RoleKeys("v1", mapOf("v1" to tokenKey)),
		)
		val json = codec.serialize(keyring)
		return KeyringManager(
			object : ServerSecretProvider { override fun loadKeyring() = json },
			codec, fileSystem, legacyPath,
		)
	}

	private fun managerNoKeyring(legacyPath: Path) = KeyringManager(
		object : ServerSecretProvider { override fun loadKeyring(): String? = null },
		codec, fileSystem, legacyPath,
	)

	@Test
	fun `hashing is deterministic for a given token key`() = runTest {
		val hasher = TokenHasher(managerWithTokenKey("token-key"), ServerSecretManager(fileSystem, secureRandom), base64)
		assertEquals(hasher.hashToken(token), hasher.hashToken(token))
	}

	@Test
	fun `different token keys produce different hashes`() = runTest {
		val ssm = ServerSecretManager(fileSystem, secureRandom)
		val a = TokenHasher(managerWithTokenKey("key-A"), ssm, base64).hashToken(token)
		val b = TokenHasher(managerWithTokenKey("key-B"), ssm, base64).hashToken(token)
		assertNotEquals(a, b)
	}

	@Test
	fun `grandfathered keyring hashes identically to the legacy secret fallback`() = runTest {
		// Existing deployment: a server.secret on disk. Grandfathered tokenHmac == that
		// secret, so it must hash exactly as the legacy fallback would for the same secret.
		val legacyPath = KeyringManager.legacySecretPath()
		fileSystem.createDirectories(legacyPath.parent!!)
		val secret = "legacy-server-secret"
		fileSystem.write(legacyPath) { writeUtf8(secret) }

		val grandfathered = TokenHasher(
			managerNoKeyring(legacyPath), ServerSecretManager(fileSystem, secureRandom), base64,
		)
		// A manager that finds no legacy file, forcing the fallback to read the same secret
		// via ServerSecretManager (which reads the standard path).
		val fallback = TokenHasher(
			managerNoKeyring("/absent".toPath()), ServerSecretManager(fileSystem, secureRandom), base64,
		)

		assertEquals(fallback.hashToken(token), grandfathered.hashToken(token))
	}

	@Test
	fun `with no keyring the auto-managed secret still produces a hash`() = runTest {
		val hasher = TokenHasher(
			managerNoKeyring("/absent".toPath()), ServerSecretManager(fileSystem, secureRandom), base64,
		)
		assertTrue(hasher.hashToken(token).isNotEmpty())
	}
}
