package com.darkrockstudios.apps.hammer.frontend.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * schema.org structured data (JSON-LD) for public pages, so search engines can present rich
 * results — author profiles, article snippets, and the site name. Each builder returns a JSON
 * string ready to drop verbatim into a `<script type="application/ld+json">` block (the header
 * template emits it via a triple-mustache when the model carries a `jsonLd` value).
 *
 * Only the root node carries `@context`; nested nodes (the author `Person`) omit it, per JSON-LD.
 */

@Serializable
private data class PersonLd(
	@SerialName("@type") val type: String = "Person",
	val name: String,
	val url: String,
	val description: String? = null,
)

@Serializable
private data class ProfilePageLd(
	@SerialName("@context") val context: String = "https://schema.org",
	@SerialName("@type") val type: String = "ProfilePage",
	val mainEntity: PersonLd,
)

@Serializable
private data class ArticleLd(
	@SerialName("@context") val context: String = "https://schema.org",
	@SerialName("@type") val type: String = "Article",
	val headline: String,
	val url: String,
	val author: PersonLd,
	val wordCount: Long? = null,
	val inLanguage: String? = null,
)

@Serializable
private data class WebSiteLd(
	@SerialName("@context") val context: String = "https://schema.org",
	@SerialName("@type") val type: String = "WebSite",
	val name: String,
	val url: String,
	val description: String? = null,
)

private val jsonLdEncoder = Json {
	encodeDefaults = true
	explicitNulls = false
}

/**
 * Neutralize `</script>` (and any other tag start) so author-supplied text — pen names, bios,
 * story titles — can't break out of the embedding `<script>` block. `<` is a valid JSON
 * escape that browsers parse back to `<`, leaving the rendered data unchanged.
 */
private fun String.escapeForScript(): String = replace("<", "\\u003c")

/** ProfilePage → Person for a community author's public page. */
fun authorProfileJsonLd(name: String, url: String, description: String? = null): String =
	jsonLdEncoder.encodeToString(
		ProfilePageLd(mainEntity = PersonLd(name = name, url = url, description = description)),
	).escapeForScript()

/** Article for a publicly-published story, crediting its author. */
fun storyArticleJsonLd(
	title: String,
	url: String,
	authorName: String,
	authorUrl: String,
	wordCount: Long? = null,
	inLanguage: String? = null,
): String =
	jsonLdEncoder.encodeToString(
		ArticleLd(
			headline = title,
			url = url,
			author = PersonLd(name = authorName, url = authorUrl),
			wordCount = wordCount,
			inLanguage = inLanguage,
		),
	).escapeForScript()

/** WebSite node for the home page. */
fun webSiteJsonLd(name: String, url: String, description: String? = null): String =
	jsonLdEncoder.encodeToString(WebSiteLd(name = name, url = url, description = description))
		.escapeForScript()
