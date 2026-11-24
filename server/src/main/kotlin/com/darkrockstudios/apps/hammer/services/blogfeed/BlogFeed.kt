package com.darkrockstudios.apps.hammer.services.blogfeed

import com.darkrockstudios.apps.hammer.isHttpUrl
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class BlogPost(
	val title: String,
	val url: String,
	val published: OffsetDateTime?,
	val categories: Set<String>,
) {
	fun inCategory(category: String): Boolean = categories.any { it.equals(category, ignoreCase = true) }
}

/** [siteUrl] is the blog's own index page, when the feed names one. */
data class BlogFeed(
	val siteUrl: String?,
	val posts: List<BlogPost>,
)

object BlogFeedParser {
	const val MAX_POSTS = 50

	/**
	 * Parses an RSS 2.0 document. Items without a title or an http(s) link are skipped. Takes bytes
	 * so the parser can honour the encoding the document declares.
	 */
	fun parse(xml: ByteArray): BlogFeed {
		val factory = DocumentBuilderFactory.newInstance().apply {
			// The feed is remote content: no DTDs, so no entity expansion or external fetches.
			setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
			setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
			isXIncludeAware = false
			isExpandEntityReferences = false
		}
		val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
		val channel = document.documentElement.child("channel")
			?: throw IllegalArgumentException("Not an RSS feed: no <channel>")

		val posts = channel.children("item").mapNotNull { item ->
			val title = item.text("title") ?: return@mapNotNull null
			val url = item.text("link")?.takeIf(::isHttpUrl) ?: return@mapNotNull null
			BlogPost(
				title = title,
				url = url,
				published = item.text("pubDate")?.let(::parseDate),
				categories = item.children("category").mapNotNull { it.textContent?.trim()?.ifEmpty { null } }.toSet(),
			)
		}.take(MAX_POSTS)

		return BlogFeed(siteUrl = channel.text("link")?.takeIf(::isHttpUrl), posts = posts)
	}

	private fun parseDate(value: String): OffsetDateTime? = try {
		OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
	} catch (_: DateTimeParseException) {
		null
	}

	private fun Element.children(name: String): List<Element> =
		(0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }

	private fun Element.child(name: String): Element? = children(name).firstOrNull()

	private fun Element.text(name: String): String? = child(name)?.textContent?.trim()?.ifEmpty { null }
}
