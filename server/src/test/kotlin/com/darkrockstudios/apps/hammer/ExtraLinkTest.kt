package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtraLinkTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `extraLinks defaults to empty`() {
		assertEquals(emptyList(), parse("").extraLinks)
	}

	@Test
	fun `an entry parses with defaults for icon and placement`() {
		val config = parse(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			""".trimIndent()
		)

		val link = config.extraLinks.single()
		assertEquals("/blog", link.url)
		assertEquals("Blog", link.title)
		assertEquals("fa-solid fa-link", link.icon)
		assertEquals(LinkPlacement.FOOTER, link.placement)
		assertEquals(emptyMap(), link.translations)
	}

	@Test
	fun `placement parses case-insensitively`() {
		val config = parse(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			placement = "Header"
			""".trimIndent()
		)

		assertEquals(LinkPlacement.HEADER, config.extraLinks.single().placement)
	}

	@Test
	fun `an inline translations table binds to its own entry`() {
		val config = parse(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			translations = { fr = "Blogue" }

			[[extraLinks]]
			url = "/shop"
			title = "Shop"
			translations = { fr = "Boutique" }
			""".trimIndent()
		)

		assertEquals(listOf("Blogue", "Boutique"), config.extraLinks.map { it.title(Locale.FRENCH) })
	}

	@Test
	fun `multiple entries are split by placement`() {
		val config = parse(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			placement = "header"

			[[extraLinks]]
			url = "/press"
			title = "Press"
			placement = "footer"

			[[extraLinks]]
			url = "/shop"
			title = "Shop"
			placement = "both"
			""".trimIndent()
		)

		assertEquals(
			listOf("Blog", "Shop"),
			config.extraLinks.filter { it.placement.inHeader }.map { it.title },
		)
		assertEquals(
			listOf("Press", "Shop"),
			config.extraLinks.filter { it.placement.inFooter }.map { it.title },
		)
	}

	@Test
	fun `title prefers an exact language tag match`() {
		val link = ExtraLink(
			url = "/blog",
			title = "Blog",
			translations = mapOf("pt" to "Blogue de Portugal", "pt-BR" to "Blogue do Brasil"),
		)

		assertEquals("Blogue do Brasil", link.title(Locale.forLanguageTag("pt-BR")))
	}

	@Test
	fun `title falls back to the language when the tag has no entry`() {
		val link = ExtraLink(url = "/blog", title = "Blog", translations = mapOf("de" to "Tagebuch"))

		assertEquals("Tagebuch", link.title(Locale.forLanguageTag("de-AT")))
	}

	@Test
	fun `title falls back to the default for an unlisted locale`() {
		val link = ExtraLink(url = "/blog", title = "Blog", translations = mapOf("fr" to "Blogue"))

		assertEquals("Blog", link.title(Locale.forLanguageTag("uk")))
	}

	@Test
	fun `an underscore locale key matches a hyphenated tag`() {
		val link = ExtraLink(url = "/blog", title = "Blog", translations = mapOf("pt_BR" to "Blogue do Brasil"))

		assertEquals("Blogue do Brasil", link.title(Locale.forLanguageTag("pt-BR")))
	}

	@Test
	fun `a locale key matches regardless of case`() {
		val link = ExtraLink(url = "/blog", title = "Blog", translations = mapOf("PT-br" to "Blogue do Brasil"))

		assertEquals("Blogue do Brasil", link.title(Locale.forLanguageTag("pt-BR")))
	}

	@Test
	fun `a hyphenated locale key survives the toml round trip`() {
		val config = parse(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			translations = { pt-BR = "Blogue do Brasil" }
			""".trimIndent()
		)

		assertEquals(
			"Blogue do Brasil",
			config.extraLinks.single().title(Locale.forLanguageTag("pt-BR")),
		)
	}

	@Test
	fun `resolve aborts on an unusable locale key`() {
		val error = assertFailsWith<IllegalArgumentException> {
			resolveWith(
				"""
				[[extraLinks]]
				url = "/blog"
				title = "Blog"
				translations = { "not a locale" = "Blogue" }
				"""
			)
		}
		assertTrue(error.message.orEmpty().contains("not a locale"))
	}

	@Test
	fun `an absolute http url is external`() {
		assertTrue(ExtraLink(url = "https://blog.example.com", title = "Blog").isExternal)
	}

	@Test
	fun `a site-relative url is not external`() {
		assertFalse(ExtraLink(url = "/blog", title = "Blog").isExternal)
	}

	@Test
	fun `resolve accepts a site-relative url`() {
		val config = resolveWith(
			"""
			[[extraLinks]]
			url = "/blog"
			title = "Blog"
			"""
		)

		assertEquals("/blog", config.extraLinks.single().url)
	}

	@Test
	fun `resolve aborts on a scheme-relative url`() {
		val error = assertFailsWith<IllegalArgumentException> {
			resolveWith(
				"""
				[[extraLinks]]
				url = "//evil.example.com"
				title = "Blog"
				"""
			)
		}
		assertTrue(error.message.orEmpty().contains("//evil.example.com"))
	}

	@Test
	fun `resolve aborts on a non-http scheme`() {
		assertFailsWith<IllegalArgumentException> {
			resolveWith(
				"""
				[[extraLinks]]
				url = "javascript:alert(1)"
				title = "Blog"
				"""
			)
		}
	}

	@Test
	fun `resolve aborts on a blank title`() {
		assertFailsWith<IllegalArgumentException> {
			resolveWith(
				"""
				[[extraLinks]]
				url = "/blog"
				title = "  "
				"""
			)
		}
	}

	@Test
	fun `resolve aborts on a blank localized title`() {
		val error = assertFailsWith<IllegalArgumentException> {
			resolveWith(
				"""
				[[extraLinks]]
				url = "/blog"
				title = "Blog"
				translations = { fr = "" }
				"""
			)
		}
		assertTrue(error.message.orEmpty().contains("fr"))
	}

	private fun resolveWith(configToml: String): ServerConfig {
		val fs = FakeFileSystem()
		val path = getRootDataDirectory(fs) / DEFAULT_CONFIG_FILE_NAME
		writeConfig(fs, path, configToml.trimIndent())
		return resolveServerConfig(configPath = null, fileSystem = fs)
	}

	private fun writeConfig(fs: FakeFileSystem, path: Path, contents: String) {
		path.parent?.let { fs.createDirectories(it) }
		fs.write(path) { writeUtf8(contents) }
	}
}
