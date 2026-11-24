package com.darkrockstudios.apps.hammer.services.blogfeed

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BlogFeedRepositoryTest {

	private val feedXml =
		"<rss><channel><item><title>Hello</title><link>https://example.com/hello</link></item></channel></rss>"

	private fun repository(vararg replies: Pair<HttpStatusCode, String>): BlogFeedRepository {
		val queue = ArrayDeque(replies.toList())
		val client = HttpClient(MockEngine { _ ->
			val (status, body) = queue.removeFirst()
			respond(body, status)
		})
		return BlogFeedRepository("https://example.com/feed.xml") { client }
	}

	@Test
	fun `holds nothing until the first refresh`() {
		assertNull(repository().current())
	}

	@Test
	fun `refresh stores the parsed feed`() = runTest {
		val repository = repository(HttpStatusCode.OK to feedXml)

		repository.refresh()

		assertEquals(listOf("Hello"), repository.current()?.posts?.map { it.title })
	}

	@Test
	fun `a failed refresh keeps the previous copy`() = runTest {
		val repository = repository(
			HttpStatusCode.OK to feedXml,
			HttpStatusCode.ServiceUnavailable to "down",
			HttpStatusCode.OK to "<html>maintenance</html>",
		)
		repository.refresh()

		assertFailsWith<IOException> { repository.refresh() }
		assertFailsWith<IllegalArgumentException> { repository.refresh() }

		assertEquals(listOf("Hello"), repository.current()?.posts?.map { it.title })
	}
}
