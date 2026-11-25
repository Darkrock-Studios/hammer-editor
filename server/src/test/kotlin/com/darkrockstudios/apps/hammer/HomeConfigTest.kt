package com.darkrockstudios.apps.hammer

import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): HomeConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString).home

	@Test
	fun `the blog feed is on by default`() {
		assertTrue(parse("").blogFeed)
	}

	@Test
	fun `the blog feed can be switched off`() {
		assertFalse(parse("[home]\nblogFeed = false").blogFeed)
	}

	@Test
	fun `the promo page is the default home`() {
		assertEquals(HomeMode.PROMO, parse("").mode)
	}

	@Test
	fun `the home mode parses case-insensitively`() {
		assertEquals(HomeMode.MINIMAL, parse("[home]\nmode = \"Minimal\"").mode)
	}
}
