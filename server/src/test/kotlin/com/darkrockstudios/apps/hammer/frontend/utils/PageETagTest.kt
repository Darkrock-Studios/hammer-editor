package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PageETagTest {

	private val model = mapOf<String, Any>(
		"title" to "A Story · Ada — Hammer",
		"version" to "1.2.3",
		"locale" to "en-US",
		"isLoggedIn" to false,
	)

	@Test
	fun `the same model and inputs produce the same validator`() {
		assertEquals(pageETag(model, "story-v1", 1), pageETag(model, "story-v1", 1))
	}

	@Test
	fun `key order does not change the validator`() {
		val reordered = linkedMapOf<String, Any>(
			"isLoggedIn" to false,
			"locale" to "en-US",
			"version" to "1.2.3",
			"title" to "A Story · Ada — Hammer",
		)

		assertEquals(pageETag(model), pageETag(reordered))
	}

	@Test
	fun `a changed model field changes the validator`() {
		val loggedIn = model + mapOf("isLoggedIn" to true, "sessionUsername" to "ada")

		assertNotEquals(pageETag(model), pageETag(loggedIn))
	}

	@Test
	fun `an added model field changes the validator`() {
		assertNotEquals(pageETag(model), pageETag(model + ("patreonUrl" to "https://example.com")))
	}

	@Test
	fun `a changed extra input changes the validator`() {
		assertNotEquals(pageETag(model, "story-v1", 1), pageETag(model, "story-v2", 1))
		assertNotEquals(pageETag(model, "story-v1", 1), pageETag(model, "story-v1", 2))
	}

	@Test
	fun `the message bundle is not hashed`() {
		val withMessages = model + mapOf(
			"msg" to mapOf("greeting" to "Hello"),
			"locales" to listOf(mapOf("tag" to "en-US")),
		)
		val withOtherMessages = model + mapOf(
			"msg" to mapOf("greeting" to "Bonjour"),
			"locales" to listOf(mapOf("tag" to "fr-FR")),
		)

		assertEquals(pageETag(withMessages), pageETag(withOtherMessages))
	}

	@Test
	fun `the validator is weak and quoted`() {
		val etag = pageETag(model)

		assertTrue(etag.startsWith("W/\""), "expected a weak validator, was $etag")
		assertTrue(etag.endsWith("\""))
	}

	@Test
	fun `a request holding the validator is answered 304 without rendering`() = testApplication {
		var renderCount = 0
		val etag = pageETag(model, "story-v1")
		routing {
			get("/story") {
				if (call.matchesETag(etag)) {
					call.applyRevalidationHeaders(etag)
					call.respond(HttpStatusCode.NotModified)
					return@get
				}
				renderCount++
				call.applyRevalidationHeaders(etag)
				call.respondText("rendered")
			}
		}

		val first = client.get("/story")
		val second = client.get("/story") { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.OK, first.status)
		assertEquals(etag, first.headers[HttpHeaders.ETag])
		assertEquals("private, no-cache", first.headers[HttpHeaders.CacheControl])
		assertEquals(HttpStatusCode.NotModified, second.status)
		assertEquals(1, renderCount, "a revalidating request should not re-render")
	}

	@Test
	fun `a request holding a stale validator is rendered`() = testApplication {
		val etag = pageETag(model, "story-v2")
		routing {
			get("/story") {
				if (call.matchesETag(etag)) {
					call.respond(HttpStatusCode.NotModified)
					return@get
				}
				call.respondText("rendered")
			}
		}

		val response = client.get("/story") {
			header(HttpHeaders.IfNoneMatch, pageETag(model, "story-v1"))
		}

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("rendered", response.bodyAsText())
	}

	@Test
	fun `a validator among several sent by the client still matches`() = testApplication {
		val etag = pageETag(model, "story-v1")
		routing {
			get("/story") {
				if (call.matchesETag(etag)) {
					call.respond(HttpStatusCode.NotModified)
					return@get
				}
				call.respondText("rendered")
			}
		}

		val response = client.get("/story") {
			header(HttpHeaders.IfNoneMatch, "${pageETag(model, "story-v0")}, $etag")
		}

		assertEquals(HttpStatusCode.NotModified, response.status)
	}
}
