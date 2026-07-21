package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HTTP validators on the server-rendered Mustache pages: that each carries one, that a caller
 * holding it is answered 304, and that every input which changes the page also changes it.
 */
class PageValidatorTest : EndToEndTest() {

	private val userId = 1L
	private val penName = "JaneAuthor"

	private fun setConfig(key: String, value: String) {
		database().serverDatabase.serverConfigQueries.upsertConfig(key, value)
	}

	/** Any account at all; without one the server is in setup mode and redirects every page. */
	private fun seedAccount() = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "jane@test.com", password = "password123!@#"), database())
	}

	private fun seedAuthor(bio: String?, communityMember: Boolean = true) {
		seedAccount()
		with(database().serverDatabase.accountQueries) {
			updatePenName(pen_name = penName, id = userId)
			updateBio(bio = bio, id = userId)
			updateCommunityMember(community_member = communityMember, id = userId)
		}
	}

	private suspend fun etagOf(path: String): String {
		val response = client().get(route(path))
		assertEquals(HttpStatusCode.OK, response.status, "$path should render")
		return assertNotNull(response.headers[HttpHeaders.ETag], "$path should carry a validator")
	}

	@Test
	fun `the home page is served with a revalidation validator`(): Unit = runBlocking {
		doStartServer()
		seedAccount()

		val response = client().get(route(""))

		assertEquals(HttpStatusCode.OK, response.status)
		val etag = assertNotNull(response.headers[HttpHeaders.ETag], "the page should carry a validator")
		assertTrue(etag.startsWith("W/\""), "the validator should be weak, was $etag")
		assertEquals("private, no-cache", response.headers[HttpHeaders.CacheControl])
		assertEquals("Cookie, Accept-Language", response.headers[HttpHeaders.Vary])
	}

	@Test
	fun `a visitor holding the home page validator is answered 304`(): Unit = runBlocking {
		doStartServer()
		seedAccount()

		val etag = etagOf("")
		val second = client().get(route("")) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, second.status)
		assertTrue(second.bodyAsText().isEmpty(), "a 304 carries no body")
	}

	@Test
	fun `changing the server message changes the home page validator`(): Unit = runBlocking {
		doStartServer()
		seedAccount()

		val before = etagOf("")
		setConfig("server_message", "The instance is moving to a new host on Friday.")

		val response = client().get(route("")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "a changed server message must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("moving to a new host"))
	}

	/**
	 * The contact email reaches the page only through a message formatted at request time, which
	 * lands in the bulky `msg` bundle that the validator deliberately skips. Without the mirror
	 * that puts formatted messages back in the hash, an admin changing the address would leave the
	 * validator identical and every browser holding it would keep showing the old address.
	 */
	@Test
	fun `changing an address that only appears in a formatted message changes the validator`(): Unit = runBlocking {
		doStartServer()
		seedAccount()
		setConfig("contact_email", "old-admin@test.com")

		val first = client().get(route(""))
		val before = assertNotNull(first.headers[HttpHeaders.ETag])
		assertTrue(first.bodyAsText().contains("old-admin@test.com"), "the formatted message should render")

		setConfig("contact_email", "new-admin@test.com")
		val response = client().get(route("")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "a changed contact address must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("new-admin@test.com"))
	}

	@Test
	fun `a reader holding the about page validator is answered 304`(): Unit = runBlocking {
		doStartServer()
		seedAccount()
		setConfig("about_server", "This instance is run by a writing group.")

		val etag = etagOf("about")
		val second = client().get(route("about")) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, second.status)
	}

	@Test
	fun `editing the about text changes the about page validator`(): Unit = runBlocking {
		doStartServer()
		seedAccount()
		setConfig("about_server", "This instance is run by a writing group.")

		val before = etagOf("about")
		setConfig("about_server", "This instance is now invite only.")

		val response = client().get(route("about")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "edited about text must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("now invite only"))
	}

	@Test
	fun `a reader holding the author page validator is answered 304`(): Unit = runBlocking {
		doStartServer()
		seedAuthor(bio = "Writes slowly, revises forever.")

		val etag = etagOf("a/$penName")
		val second = client().get(route("a/$penName")) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, second.status)
	}

	@Test
	fun `editing an author bio changes the author page validator`(): Unit = runBlocking {
		doStartServer()
		seedAuthor(bio = "Writes slowly, revises forever.")

		val before = etagOf("a/$penName")
		database().serverDatabase.accountQueries.updateBio(bio = "Now writing science fiction.", id = userId)

		val response = client().get(route("a/$penName")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "an edited bio must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("Now writing science fiction."))
	}

	/**
	 * `community_member` gates the X-Robots-Tag header, which never reaches the model. It is passed
	 * to the validator explicitly, so two responses differing only in that header can't share one.
	 */
	@Test
	fun `leaving the community changes the author page validator`(): Unit = runBlocking {
		doStartServer()
		seedAuthor(bio = "Writes slowly, revises forever.", communityMember = true)

		val before = etagOf("a/$penName")
		database().serverDatabase.accountQueries.updateCommunityMember(community_member = false, id = userId)

		val response = client().get(route("a/$penName")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "a de-indexed author page must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"])
	}
}
