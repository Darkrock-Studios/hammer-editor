package com.darkrockstudios.apps.hammer

import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HomeConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): HomeConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString).home

	@Test
	fun `nothing is configured by default`() {
		val home = parse("")

		assertNull(home.blogFeedUrl)
		assertNull(home.tutorialsUrl)
		assertEquals("principles", home.blogFeaturedCategory)
		home.validate()
	}

	@Test
	fun `a home block parses`() {
		val home = parse(
			"""
			[home]
			blogFeedUrl = "https://example.com/blog/feed.xml"
			blogFeaturedCategory = "essays"
			tutorialsUrl = "https://videos.example.com/tutorials"
			""".trimIndent()
		)

		assertEquals("https://example.com/blog/feed.xml", home.blogFeedUrl)
		assertEquals("essays", home.blogFeaturedCategory)
		assertEquals("https://videos.example.com/tutorials", home.tutorialsUrl)
		home.validate()
	}

	@Test
	fun `a feed address that is not http is refused`() {
		assertFailsWith<IllegalArgumentException> { HomeConfig(blogFeedUrl = "file:///etc/passwd").validate() }
		assertFailsWith<IllegalArgumentException> { HomeConfig(blogFeedUrl = "/blog/feed.xml").validate() }
	}

	@Test
	fun `a tutorials address that is not http is refused`() {
		assertFailsWith<IllegalArgumentException> { HomeConfig(tutorialsUrl = "javascript:alert(1)").validate() }
	}
}
