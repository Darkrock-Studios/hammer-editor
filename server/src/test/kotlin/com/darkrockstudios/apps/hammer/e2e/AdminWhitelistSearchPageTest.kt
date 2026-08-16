package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Drives the allowed-users email search over real HTTP, so the query's trip through the
 * route, the repository and the rendered fragment is exercised end to end.
 */
class AdminWhitelistSearchPageTest : EndToEndTest() {

	private val email = "admin@test.com"
	private val password = "password123!@#"

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		addEntries(email)
	}

	private fun addEntries(vararg emails: String) {
		emails.forEach {
			database().serverDatabase.whiteListQueries
				.addToWhiteList(it, Clock.System.now(), "Beta tester", null)
		}
	}

	private suspend fun login(): HttpClient {
		val authed = HttpClient { install(HttpCookies) }
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	private suspend fun HttpClient.fragment(query: String = ""): String {
		val response = get(route("admin/allowed-users/user-fragment$query")) {
			header("HX-Request", "true")
		}
		assertEquals(HttpStatusCode.OK, response.status)
		return response.bodyAsText()
	}

	private suspend fun HttpClient.postFragment(action: String, body: String): String {
		val response = post(route("admin/allowed-users/$action")) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(body)
		}
		assertEquals(HttpStatusCode.OK, response.status)
		return response.bodyAsText()
	}

	@Test
	fun `a search filters the list to matching emails`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com", "alicia@example.com", "bob@example.com")

		login().use { authed ->
			val body = authed.fragment("?q=alic")

			assertContains(body, "alice@example.com")
			assertContains(body, "alicia@example.com")
			assertFalse(body.contains("bob@example.com"), "Non-matching entries must be filtered out")
		}
	}

	@Test
	fun `no search lists everything`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com", "bob@example.com")

		login().use { authed ->
			val body = authed.fragment()

			assertContains(body, "alice@example.com")
			assertContains(body, "bob@example.com")
		}
	}

	@Test
	fun `pagination is scoped to the filtered set`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries(*(1..15).map { "alice$it@example.com" }.toTypedArray())
		addEntries("bob@example.com")

		login().use { authed ->
			val firstPage = authed.fragment("?q=alice")
			val secondPage = authed.fragment("?q=alice&page=1")

			assertContains(firstPage, "of 2", message = "15 matches over a page size of 10 is two pages")
			assertEquals(10, firstPage.split("class=\"whitelist-entry\"").size - 1)
			assertEquals(5, secondPage.split("class=\"whitelist-entry\"").size - 1)
			assertFalse(secondPage.contains("bob@example.com"), "The filter must survive paging")
		}
	}

	@Test
	fun `the sort and pagination controls carry the query forward`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries(*(1..15).map { "alice$it@example.com" }.toTypedArray())

		login().use { authed ->
			val body = authed.fragment("?q=alice")

			assertContains(body, "sortOldestFirst=true&q=alice")
			assertContains(body, "page=1&sortOldestFirst=false&q=alice")
		}
	}

	@Test
	fun `the remove form carries the query so the filter survives a removal`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com", "alicia@example.com", "bob@example.com")

		login().use { authed ->
			val listed = authed.fragment("?q=alic")
			assertContains(listed, "name=\"q\" value=\"alic\"")

			val afterRemove = authed.postFragment(
				"remove",
				"email=${URLEncoder.encode("alicia@example.com", "UTF-8")}&page=0&sortOldestFirst=false&q=alic"
			)

			assertContains(afterRemove, "alice@example.com")
			assertFalse(afterRemove.contains("alicia@example.com"), "The removed entry should be gone")
			assertFalse(afterRemove.contains("bob@example.com"), "The filter must survive the removal")
		}
	}

	@Test
	fun `editing a reason keeps the filtered view`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com", "bob@example.com")

		login().use { authed ->
			val body = authed.postFragment(
				"edit-reason",
				"email=${URLEncoder.encode("alice@example.com", "UTF-8")}&reason=Friend&page=0&sortOldestFirst=false&q=alice"
			)

			assertContains(body, "alice@example.com")
			assertContains(body, "Friend")
			assertFalse(body.contains("bob@example.com"), "The filter must survive the edit")
		}
	}

	@Test
	fun `adding returns the unfiltered first page so the new entry is visible`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("bob@example.com")

		login().use { authed ->
			val body = authed.postFragment(
				"add",
				"email=${URLEncoder.encode("carol@example.com", "UTF-8")}" +
					"&reason=Beta+tester&expiryPreset=never&expiryDate=&page=3&q=alice"
			)

			assertContains(body, "carol@example.com", message = "The new entry must be visible")
			assertContains(body, "bob@example.com", message = "The add response is deliberately unfiltered")
			assertFalse(body.contains("whitelist-controls__filter"), "No filter chip should render")
		}
	}

	@Test
	fun `a search with no matches renders its own empty state`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com")

		login().use { authed ->
			val body = authed.fragment("?q=nobody")

			assertContains(body, "No allowed users match that email.")
			assertFalse(
				body.contains("No allowed users yet"),
				"The add-your-first empty state is wrong when a filter is active",
			)
		}
	}

	@Test
	fun `an active filter is surfaced with a clear control`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com")

		login().use { authed ->
			assertContains(authed.fragment("?q=alice"), "whitelist-controls__filter")
			assertFalse(
				authed.fragment().contains("whitelist-controls__filter"),
				"An unfiltered list should show no filter chip",
			)
		}
	}

	@Test
	fun `a search with no matches renders no pagination bar`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com")

		login().use { authed ->
			val body = authed.fragment("?q=nobody")

			assertFalse(
				body.contains("class=\"pagination\""),
				"Zero matches means zero pages, so no pagination bar should render",
			)
		}
	}

	@Test
	fun `the fragment exposes its sort state so a search can preserve it`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com")

		login().use { authed ->
			assertContains(
				authed.fragment("?sortOldestFirst=true"),
				"id=\"whitelist-sort-state\"",
			)
			assertContains(
				authed.fragment("?sortOldestFirst=true"),
				"name=\"sortOldestFirst\" value=\"true\"",
			)
		}
	}

	@Test
	fun `a rejected add keeps the admin's filter and page`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com", "bob@example.com")

		login().use { authed ->
			val body = authed.postFragment(
				"add",
				"email=not-an-email&reason=Beta+tester&expiryPreset=never&expiryDate=&page=0&sortOldestFirst=false&q=alice"
			)

			assertContains(body, "Invalid email address format")
			assertContains(body, "alice@example.com")
			assertFalse(
				body.contains("bob@example.com"),
				"A validation error is not an add, so the admin's filter must survive it",
			)
		}
	}

	@Test
	fun `a successful add signals the client to reset the form`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val ok = authed.post(route("admin/allowed-users/add")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody(
					"email=${URLEncoder.encode("carol@example.com", "UTF-8")}" +
						"&reason=Beta+tester&expiryPreset=never&expiryDate=&q=alice"
				)
			}
			assertEquals(HttpStatusCode.OK, ok.status)
			assertEquals(
				"whitelist-added",
				ok.headers["HX-Trigger"],
				"Only a real add may tell the client to clear the form and search",
			)

			val rejected = authed.post(route("admin/allowed-users/add")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("email=not-an-email&reason=Beta+tester&expiryPreset=never&expiryDate=&q=alice")
			}
			assertEquals(HttpStatusCode.OK, rejected.status)
			assertEquals(null, rejected.headers["HX-Trigger"], "A rejected add must not signal a reset")
		}
	}

	@Test
	fun `a query containing markup is escaped in both the body and the reflected links`(): Unit = runBlocking {
		doStartServer()
		seed()
		addEntries("alice@example.com")

		login().use { authed ->
			val hostile = "<script>alert(1)</script>"
			val body = authed.fragment("?q=${URLEncoder.encode(hostile, "UTF-8")}")

			assertFalse(body.contains("<script>alert(1)</script>"), "Raw markup must never be reflected")
			assertContains(body, "&lt;script&gt;")
			assertTrue(
				body.contains("q=%3Cscript%3E"),
				"The query must be URL encoded where it is reflected into an hx-get",
			)
		}
	}
}
