package com.darkrockstudios.apps.hammer.services.blogfeed

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/**
 * Holds the last successfully fetched copy of the blog feed, so the home page never waits on, or
 * fails with, another site. Null [feedUrl] means the feed is switched off.
 */
class BlogFeedRepository(
	private val feedUrl: String?,
	createClient: () -> HttpClient = ::defaultClient,
) {
	private val httpClient: HttpClient by lazy(createClient)

	@Volatile
	private var feed: BlogFeed? = null

	val isConfigured: Boolean get() = feedUrl != null

	fun current(): BlogFeed? = feed

	/** Throws when the feed can't be fetched or parsed; the previous copy is kept. */
	suspend fun refresh() {
		val url = feedUrl ?: return
		val response = httpClient.get(url)
		if (!response.status.isSuccess()) throw IOException("Blog feed returned ${response.status}")
		val body = response.bodyAsChannel().readBuffer(MAX_FEED_BYTES + 1).readByteArray()
		if (body.size > MAX_FEED_BYTES) throw IOException("Blog feed is over $MAX_FEED_BYTES bytes")
		feed = BlogFeedParser.parse(body)
	}

	companion object {
		const val MAX_FEED_BYTES = 2L * 1024 * 1024

		private fun defaultClient() = HttpClient(Java) {
			expectSuccess = false
			install(HttpTimeout) {
				connectTimeoutMillis = 5_000
				requestTimeoutMillis = 15_000
			}
		}
	}
}
