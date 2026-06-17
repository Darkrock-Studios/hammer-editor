package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EncryptionConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted encryption section defaults to AES`() {
		val config = parse(
			"""
			host = "localhost"
			port = 8080
			""".trimIndent()
		)
		assertEquals(EncryptionMode.AES, config.encryption.mode)
	}

	@Test
	fun `Mode none parses`() {
		val config = parse(
			"""
			[encryption]
			mode = "none"
			""".trimIndent()
		)
		assertEquals(EncryptionMode.NONE, config.encryption.mode)
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
