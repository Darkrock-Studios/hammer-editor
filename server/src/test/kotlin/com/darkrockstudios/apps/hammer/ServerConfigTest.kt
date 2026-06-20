package com.darkrockstudios.apps.hammer

import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted bindHosts defaults to all IPv4 interfaces`() {
		assertEquals(listOf("0.0.0.0"), parse("").bindHosts)
	}

	@Test
	fun `bindHosts parses loopback addresses`() {
		val config = parse("""bindHosts = ["127.0.0.1", "::1"]""")
		assertEquals(listOf("127.0.0.1", "::1"), config.bindHosts)
	}
}
