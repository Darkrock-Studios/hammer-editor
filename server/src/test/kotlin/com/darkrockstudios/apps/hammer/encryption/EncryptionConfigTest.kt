package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncryptionConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted encryption section is unspecified and writes plaintext`() {
		val config = parse(
			"""
			host = "localhost"
			port = 8080
			""".trimIndent()
		)
		assertNull(config.encryption.mode)
		assertEquals(EncryptionMode.NONE, config.encryption.effectiveWriteMode())
	}

	@Test
	fun `Mode aes parses`() {
		val config = parse(
			"""
			[encryption]
			mode = "aes"
			""".trimIndent()
		)
		assertEquals(EncryptionMode.AES, config.encryption.mode)
	}

	@Test
	fun `Mode is case-insensitive`() {
		val config = parse(
			"""
			[encryption]
			mode = "AES"
			""".trimIndent()
		)
		assertEquals(EncryptionMode.AES, config.encryption.mode)
	}
}
