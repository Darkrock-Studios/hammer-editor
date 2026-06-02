package com.darkrockstudios.apps.hammer

import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class StorageConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	private fun storageWithType(type: String): String =
		"""
		[storage]
		type = "$type"

		[storage.remote]
		host = "localhost"
		database = "hammer"
		user = "hammer"
		password = "secret"
		""".trimIndent()

	@Test
	fun `Omitted storage section defaults to EMBEDDED`() {
		assertEquals(StorageMode.EMBEDDED, parse("").storage.type)
	}

	@Test
	fun `storage type decodes regardless of case`() {
		for (variant in listOf("remote", "REMOTE", "Remote", "rEmOtE")) {
			assertEquals(StorageMode.REMOTE, parse(storageWithType(variant)).storage.type)
		}
		assertEquals(StorageMode.EMBEDDED, parse(storageWithType("EMBEDDED")).storage.type)
	}

	@Test
	fun `analytics type decodes regardless of case`() {
		val config = parse(
			"""
			[analytics]
			type = "UMAMI"

			[analytics.umami]
			websiteId = "abc-123"
			""".trimIndent()
		)
		assertEquals(AnalyticsProviderType.UMAMI, config.analytics.type)
	}

	@Test
	fun `unknown storage type throws`() {
		assertThrows<Exception> { parse(storageWithType("bogus")) }
	}
}
