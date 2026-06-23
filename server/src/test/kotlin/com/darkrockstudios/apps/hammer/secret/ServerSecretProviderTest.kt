package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.SecretConfig
import com.darkrockstudios.apps.hammer.SecretProviderType
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerSecretProviderTest {

	private val fileSystem = FakeFileSystem()
	private val path = "/keyring.json".toPath()

	private fun write(contents: String) {
		fileSystem.write(path) { writeUtf8(contents) }
	}

	@Test
	fun `file provider returns null when the file is absent`() {
		assertNull(FileSecretProvider(fileSystem, path).loadKeyring())
	}

	@Test
	fun `file provider treats an empty file as absent`() {
		write("")
		assertNull(FileSecretProvider(fileSystem, path).loadKeyring())
	}

	@Test
	fun `file provider treats a whitespace-only file as absent`() {
		write("   \n\t ")
		assertNull(FileSecretProvider(fileSystem, path).loadKeyring())
	}

	@Test
	fun `file provider returns the file contents`() {
		write("""{"schema":1}""")
		assertEquals("""{"schema":1}""", FileSecretProvider(fileSystem, path).loadKeyring())
	}

	@Test
	fun `env provider returns null when the var is unset`() {
		assertNull(EnvSecretProvider("HAMMER_KEYRING") { null }.loadKeyring())
	}

	@Test
	fun `env provider treats a blank var as absent`() {
		assertNull(EnvSecretProvider("HAMMER_KEYRING") { "  " }.loadKeyring())
	}

	@Test
	fun `env provider returns the variable contents`() {
		assertEquals("""{"schema":1}""", EnvSecretProvider("HAMMER_KEYRING") { """{"schema":1}""" }.loadKeyring())
	}

	@Test
	fun `buildSecretProvider selects a file provider that reads the configured path`() {
		write("""{"schema":1}""")
		val config = SecretConfig(provider = SecretProviderType.FILE, file = path.toString())

		assertEquals("""{"schema":1}""", buildSecretProvider(config, fileSystem).loadKeyring())
	}

	@Test
	fun `buildSecretProvider selects the env provider`() {
		val config = SecretConfig(provider = SecretProviderType.ENV, envVar = "HAMMER_KEYRING")

		assertIs<EnvSecretProvider>(buildSecretProvider(config, fileSystem))
	}
}
