package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.ServerConfig
import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AnalyticsConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted analytics section defaults to NONE`() {
		val config = parse(
			"""
			host = "localhost"
			port = 8080
			""".trimIndent()
		)
		assertEquals(AnalyticsProviderType.NONE, config.analytics.type)
		assertNull(config.analytics.umami)
	}

	@Test
	fun `Umami block parses with default script URL`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = "abc-123"
			""".trimIndent()
		)
		assertEquals(AnalyticsProviderType.UMAMI, config.analytics.type)
		assertEquals("abc-123", config.analytics.umami?.websiteId)
		assertEquals("https://cloud.umami.is/script.js", config.analytics.umami?.scriptUrl)
	}

	@Test
	fun `Umami block honors a custom self-hosted script URL`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = "abc-123"
			scriptUrl = "https://umami.example.com/script.js"
			""".trimIndent()
		)
		assertEquals("https://umami.example.com/script.js", config.analytics.umami?.scriptUrl)
	}

	@Test
	fun `validate succeeds for NONE`() {
		parse("").analytics.validate()
	}

	@Test
	fun `validate throws when umami selected without config block`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"
			""".trimIndent()
		)
		assertThrows<IllegalArgumentException> { config.analytics.validate() }
	}

	@Test
	fun `validate throws on blank websiteId`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = ""
			""".trimIndent()
		)
		assertThrows<IllegalArgumentException> { config.analytics.validate() }
	}

	@Test
	fun `validate throws on a malformed script URL`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = "abc-123"
			scriptUrl = "https://umami.is/bad script.js"
			""".trimIndent()
		)
		assertThrows<IllegalArgumentException> { config.analytics.validate() }
	}

	@Test
	fun `validate throws on a non-http script URL`() {
		val config = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = "abc-123"
			scriptUrl = "ftp://umami.is/script.js"
			""".trimIndent()
		)
		assertThrows<IllegalArgumentException> { config.analytics.validate() }
	}

	@Test
	fun `provider is resolved once when umami configured and absent otherwise`() {
		val umami = parse(
			"""
			[analytics]
			type = "umami"

			[analytics.umami]
			websiteId = "abc-123"
			""".trimIndent()
		)
		val provider = umami.analytics.provider
		assertNotNull(provider)
		// Same precomputed instance is reused, not rebuilt per access.
		assertSame(provider, umami.analytics.provider)

		assertNull(parse("").analytics.provider)
	}
}
