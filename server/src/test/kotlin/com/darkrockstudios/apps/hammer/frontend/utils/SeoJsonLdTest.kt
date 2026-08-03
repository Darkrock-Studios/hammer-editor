package com.darkrockstudios.apps.hammer.frontend.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeoJsonLdTest {

	private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

	@Test
	fun `author profile is a ProfilePage wrapping a Person`() {
		val obj = parse(
			authorProfileJsonLd(
				name = "Jane Doe",
				url = "https://hammer.ink/a/Jane-Doe",
				description = "Writes about maps.",
			),
		)

		assertEquals("https://schema.org", obj["@context"]!!.jsonPrimitive.content)
		assertEquals("ProfilePage", obj["@type"]!!.jsonPrimitive.content)
		val person = obj["mainEntity"]!!.jsonObject
		assertEquals("Person", person["@type"]!!.jsonPrimitive.content)
		assertEquals("Jane Doe", person["name"]!!.jsonPrimitive.content)
		assertEquals("https://hammer.ink/a/Jane-Doe", person["url"]!!.jsonPrimitive.content)
		assertEquals("Writes about maps.", person["description"]!!.jsonPrimitive.content)
		// Nested nodes must not repeat @context.
		assertNull(person["@context"])
	}

	@Test
	fun `story article credits its author and word count`() {
		val obj = parse(
			storyArticleJsonLd(
				title = "The Cartographer of Forgotten Roads",
				url = "https://hammer.ink/a/Jane-Doe/story-1",
				authorName = "Jane Doe",
				authorUrl = "https://hammer.ink/a/Jane-Doe",
				wordCount = 42000,
			),
		)

		assertEquals("Article", obj["@type"]!!.jsonPrimitive.content)
		assertEquals("The Cartographer of Forgotten Roads", obj["headline"]!!.jsonPrimitive.content)
		assertEquals(42000, obj["wordCount"]!!.jsonPrimitive.content.toLong())
		assertEquals("Jane Doe", obj["author"]!!.jsonObject["name"]!!.jsonPrimitive.content)
	}

	@Test
	fun `story article carries inLanguage when set`() {
		val obj = parse(
			storyArticleJsonLd(
				title = "Untitled",
				url = "https://hammer.ink/a/x/y",
				authorName = "X",
				authorUrl = "https://hammer.ink/a/x",
				inLanguage = "fr",
			),
		)

		assertEquals("fr", obj["inLanguage"]!!.jsonPrimitive.content)
	}

	@Test
	fun `website node carries name and url`() {
		val obj = parse(webSiteJsonLd(name = "Hammer", url = "https://hammer.ink/", description = "A writing tool."))

		assertEquals("WebSite", obj["@type"]!!.jsonPrimitive.content)
		assertEquals("Hammer", obj["name"]!!.jsonPrimitive.content)
		assertEquals("https://hammer.ink/", obj["url"]!!.jsonPrimitive.content)
	}

	@Test
	fun `omits null optional fields`() {
		val obj = parse(webSiteJsonLd(name = "Hammer", url = "https://hammer.ink/"))
		assertNull(obj["description"])

		val article = parse(
			storyArticleJsonLd(
				title = "Untitled",
				url = "https://hammer.ink/a/x/y",
				authorName = "X",
				authorUrl = "https://hammer.ink/a/x",
			),
		)
		assertNull(article["wordCount"])
		assertNull(article["inLanguage"])
	}

	@Test
	fun `escapes tag starts so text cannot break out of the script block`() {
		val raw = authorProfileJsonLd(
			name = "</script><script>alert(1)</script>",
			url = "https://hammer.ink/a/x",
		)

		assertFalse(raw.contains("</script>"), "raw output must not contain a literal </script>")
		assertFalse(raw.contains("<script>"), "raw output must not contain a literal <script>")
		assertTrue(raw.contains("\\u003c"), "tag starts should be escaped as \\u003c")
		// Still valid JSON, and the escape decodes back to the original text.
		val person = parse(raw)["mainEntity"]!!.jsonObject
		assertEquals("</script><script>alert(1)</script>", person["name"]!!.jsonPrimitive.content)
	}
}
