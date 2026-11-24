package com.darkrockstudios.apps.hammer.services.blogfeed

import com.darkrockstudios.apps.hammer.HammerLinks
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import com.darkrockstudios.apps.hammer.utilities.outboundHttpClient
import com.darkrockstudios.apps.hammer.utilities.readCapped
import kotlinx.io.IOException

/**
 * Holds the last successfully fetched copy of the blog feed, so the home page never waits on, or
 * fails with, another site.
 */
class BlogFeedRepository(
	private val feedUrl: String = HammerLinks.BLOG_FEED,
	createClient: () -> HttpClient = ::defaultClient,
) {
	private val httpClient: HttpClient by lazy(createClient)

	@Volatile
	private var feed: BlogFeed? = null

	fun current(): BlogFeed? = feed

	/** Throws when the feed can't be fetched or parsed; the previous copy is kept. */
	suspend fun refresh() {
		val response = httpClient.get(feedUrl)
		if (!response.status.isSuccess()) throw IOException("Blog feed returned ${response.status}")
		val body = response.readCapped(MAX_FEED_BYTES) ?: throw IOException("Blog feed is over $MAX_FEED_BYTES bytes")
		feed = BlogFeedParser.parse(body)
	}

	companion object {
		const val MAX_FEED_BYTES = 2L * 1024 * 1024

		private fun defaultClient() = outboundHttpClient(requestTimeoutMillis = 15_000, followRedirects = true)
	}
}
