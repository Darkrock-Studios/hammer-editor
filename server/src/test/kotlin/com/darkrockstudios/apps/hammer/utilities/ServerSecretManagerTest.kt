package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
class ServerSecretManagerTest {

	private val fileSystem = FakeFileSystem()

	@Test
	fun `generates a full-entropy 32-byte secret on first access`() = runTest {
		val secret = ServerSecretManager(fileSystem, SecureRandom()).getServerSecret()
		// Round-trips as 32 raw bytes — no lossy UTF-8 collapse.
		assertEquals(32, Base64.decode(secret).size)
	}

	@Test
	fun `persists and reloads the same secret across instances`() = runTest {
		val first = ServerSecretManager(fileSystem, SecureRandom()).getServerSecret()
		val reloaded = ServerSecretManager(fileSystem, SecureRandom()).getServerSecret()
		assertEquals(first, reloaded)
	}
}
