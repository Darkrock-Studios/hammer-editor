package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.SecretProviderType
import com.darkrockstudios.apps.hammer.ServerConfig
import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecretConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted secret section defaults to the file provider`() {
		val config = parse("host = \"localhost\"")
		assertEquals(SecretProviderType.FILE, config.secret.provider)
		assertNull(config.secret.file)
		assertEquals("HAMMER_KEYRING", config.secret.envVar)
	}

	@Test
	fun `File provider with a custom path parses`() {
		val config = parse(
			"""
			[secret]
			provider = "file"
			file = "/etc/hammer/keyring.json"
			""".trimIndent()
		)
		assertEquals(SecretProviderType.FILE, config.secret.provider)
		assertEquals("/etc/hammer/keyring.json", config.secret.file)
	}

	@Test
	fun `Env provider parses with a custom variable name`() {
		val config = parse(
			"""
			[secret]
			provider = "env"
			envVar = "MY_KEYRING"
			""".trimIndent()
		)
		assertEquals(SecretProviderType.ENV, config.secret.provider)
		assertEquals("MY_KEYRING", config.secret.envVar)
	}

	@Test
	fun `Provider is case-insensitive`() {
		val config = parse(
			"""
			[secret]
			provider = "ENV"
			""".trimIndent()
		)
		assertEquals(SecretProviderType.ENV, config.secret.provider)
	}
}
