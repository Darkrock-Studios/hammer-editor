package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.plugin.AllowedUsersSource
import com.darkrockstudios.apps.hammer.plugin.NoticeSlot
import com.darkrockstudios.apps.hammer.plugin.ServerPlugin
import com.darkrockstudios.apps.hammer.utilities.Msg
import io.ktor.server.application.ApplicationCall
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * Drives the whitelist admin page over real HTTP so the expiry controls, the
 * rendered fragment, and the persisted column are all exercised together.
 */
class AdminWhitelistExpiryPageTest : EndToEndTest() {

	// A minimal syncing source, standing in for integrations that own their whitelist entries.
	private class TestSource : AllowedUsersSource {
		override val id = SOURCE_REASON
		override suspend fun isActive() = true
		override suspend fun notice(call: ApplicationCall, slot: NoticeSlot): String? = null
		override suspend fun rejectionMessage(): Msg? = null
	}

	override val serverPlugins = super.serverPlugins + object : ServerPlugin {
		override val id = SOURCE_REASON
		override fun allowedUsersSource(): AllowedUsersSource = TestSource()
	}

	private val email = "admin@test.com"
	private val password = "password123!@#"

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		database().serverDatabase.whiteListQueries
			.addToWhiteList(email, Clock.System.now(), "Test admin", null)
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

	private suspend fun HttpClient.addEntry(target: String, preset: String, date: String = ""): String {
		val response = post(route("admin/allowed-users/add")) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(target, "UTF-8")}" +
					"&reason=Beta+tester&expiryPreset=$preset" +
					"&expiryDate=${URLEncoder.encode(date, "UTF-8")}"
			)
		}
		assertEquals(HttpStatusCode.OK, response.status)
		return response.bodyAsText()
	}

	private fun storedExpiry(target: String) =
		database().serverDatabase.whiteListQueries.getAll().executeAsList()
			.single { it.email == target }
			.expires

	@Test
	fun `adding with the never preset stores no expiry and renders no expiry chip`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val body = authed.addEntry("forever@example.com", "never")

			assertContains(body, "forever@example.com")
			assertNull(storedExpiry("forever@example.com"), "Never preset must store a null expiry")
			assertFalse(body.contains("whitelist-entry__expiry--set"), "No expiry chip should render")
		}
	}

	@Test
	fun `adding with a day preset stores an expiry and renders it`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val body = authed.addEntry("temp@example.com", "30")

			val expires = assertNotNull(storedExpiry("temp@example.com"), "Preset must store an expiry")
			val expected = Clock.System.now() + kotlin.time.Duration.parse("30d")
			assertEquals(
				expected.toEpochMilliseconds().toDouble(),
				expires.toEpochMilliseconds().toDouble(),
				60_000.0,
				"Should expire ~30 days out",
			)
			assertContains(body, "whitelist-entry__expiry--set")
		}
	}

	@Test
	fun `adding with a custom date keeps the entry through the whole chosen day`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val target = LocalDate.now(ZoneId.systemDefault()).plusDays(10)
			authed.addEntry("custom@example.com", "custom", target.toString())

			val expires = assertNotNull(storedExpiry("custom@example.com"))
			val endOfChosenDay = target.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
			assertEquals(
				endOfChosenDay.toEpochMilli(),
				expires.toEpochMilliseconds(),
				"Custom date should expire at the end of that day",
			)
		}
	}

	@Test
	fun `a past custom date is rejected with an error and adds nothing`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
			val body = authed.addEntry("past@example.com", "custom", yesterday.toString())

			assertContains(body, "error-message")
			assertFalse(body.contains("past@example.com"), "Rejected entry must not be added")
			assertFalse(
				database().serverDatabase.whiteListQueries.getAll().executeAsList()
					.any { it.email == "past@example.com" },
			)
		}
	}

	@Test
	fun `an unparseable custom date is rejected`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val body = authed.addEntry("bogus@example.com", "custom", "not-a-date")

			assertContains(body, "error-message")
			assertFalse(body.contains("bogus@example.com"))
		}
	}

	@Test
	fun `edit-expiry sets then clears an entry's expiry`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			authed.addEntry("edit@example.com", "never")
			assertNull(storedExpiry("edit@example.com"))

			val set = authed.post(route("admin/allowed-users/edit-expiry")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("email=${URLEncoder.encode("edit@example.com", "UTF-8")}&expiryPreset=7")
			}
			assertEquals(HttpStatusCode.OK, set.status)
			assertNotNull(storedExpiry("edit@example.com"), "Expiry should have been set")

			val clear = authed.post(route("admin/allowed-users/edit-expiry")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("email=${URLEncoder.encode("edit@example.com", "UTF-8")}&expiryPreset=never")
			}
			assertEquals(HttpStatusCode.OK, clear.status)
			assertNull(storedExpiry("edit@example.com"), "Expiry should have been cleared")
		}
	}

	@Test
	fun `edit-expiry day preset extends the entry's current expiry`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			// Seed a known future expiry directly.
			val current = Clock.System.now() + kotlin.time.Duration.parse("10d")
			authed.addEntry("ext@example.com", "never")
			database().serverDatabase.whiteListQueries.updateExpiry(current, "ext@example.com")

			val resp = authed.post(route("admin/allowed-users/edit-expiry")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("email=${URLEncoder.encode("ext@example.com", "UTF-8")}&expiryPreset=30")
			}
			assertEquals(HttpStatusCode.OK, resp.status)

			val extended = assertNotNull(storedExpiry("ext@example.com"))
			val expected = current + kotlin.time.Duration.parse("30d")   // ~40 days out, not 30
			assertEquals(
				expected.toEpochMilliseconds().toDouble(),
				extended.toEpochMilliseconds().toDouble(),
				60_000.0,
				"A day preset must add to the current expiry, not reset to N days from now",
			)
		}
	}

	@Test
	fun `edit-expiry day preset on a lapsed entry extends from now`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			val past = Clock.System.now() - kotlin.time.Duration.parse("5d")
			authed.addEntry("lapsed2@example.com", "never")
			database().serverDatabase.whiteListQueries.updateExpiry(past, "lapsed2@example.com")

			val resp = authed.post(route("admin/allowed-users/edit-expiry")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("email=${URLEncoder.encode("lapsed2@example.com", "UTF-8")}&expiryPreset=30")
			}
			assertEquals(HttpStatusCode.OK, resp.status)

			val extended = assertNotNull(storedExpiry("lapsed2@example.com"))
			val expected = Clock.System.now() + kotlin.time.Duration.parse("30d")
			assertEquals(
				expected.toEpochMilliseconds().toDouble(),
				extended.toEpochMilliseconds().toDouble(),
				120_000.0,
				"Extending a lapsed entry must count from now, not from the past expiry",
			)
		}
	}

	/** The single `.whitelist-entry` block for [target], so per-row markup can be asserted. */
	private fun rowFor(body: String, target: String): String =
		body.split("class=\"whitelist-entry\"")
			.single { it.contains(target) }

	@Test
	fun `source-managed entries render without an expiry control but others keep one`(): Unit = runBlocking {
		doStartServer()
		seed()

		database().serverDatabase.whiteListQueries.addToWhiteList(
			"patron@example.com",
			Clock.System.now(),
			SOURCE_REASON,
			null,
		)

		login().use { authed ->
			val body = authed.get(route("admin/allowed-users/user-fragment")) {
				header("HX-Request", "true")
			}.bodyAsText()

			assertContains(body, "patron@example.com")
			assertFalse(
				rowFor(body, "patron@example.com").contains("edit-expiry-btn"),
				"Source-owned entries must not offer an expiry control",
			)
			assertContains(
				rowFor(body, email),
				"edit-expiry-btn",
				message = "A manually-added entry should still offer one",
			)
		}
	}

	@Test
	fun `an expired entry no longer authorizes the web session`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			// The admin's own entry lapses; admins are exempt from the whitelist gate,
			// so use a separate account to observe the effect.
			val userEmail = "lapsed@example.com"
			val userPassword = "password456!@#"
			E2eTestData.createAccount(TestAccount(userEmail, userPassword), database())
			authed.addEntry(userEmail, "never")

			// Expire it directly — the clock can't be moved in a booted server.
			database().serverDatabase.whiteListQueries.updateExpiry(
				Clock.System.now() - kotlin.time.Duration.parse("1h"),
				userEmail,
			)

			val user = HttpClient { install(HttpCookies) }
			val response = user.post(route("login")) {
				contentType(ContentType.Application.FormUrlEncoded)
				setBody(
					"email=${URLEncoder.encode(userEmail, "UTF-8")}" +
						"&password=${URLEncoder.encode(userPassword, "UTF-8")}"
				)
			}
			assertFalse(
				response.status == HttpStatusCode.Found,
				"A lapsed whitelist entry must not permit login",
			)
			user.close()
		}
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
	fun `add validation errors re-render the fragment and create no entry`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			assertContains(authed.postFragment("add", "email="), "Email address is required")
			assertContains(authed.postFragment("add", "email=not-an-email"), "Invalid email address format")
			assertContains(
				authed.postFragment("add", "email=valid%40example.com&reason=${"x".repeat(40)}"),
				"Reason must be 32 characters or less",
			)
			assertContains(
				authed.postFragment("add", "email=valid%40example.com&expiryPreset=-5"),
				"Expiry date must be a valid date in the future",
			)

			val emails = database().serverDatabase.whiteListQueries.getAll().executeAsList().map { it.email }
			assertFalse(emails.contains("valid@example.com"), "A rejected submission must not create an entry")
		}
	}

	@Test
	fun `edit-reason updates the entry and validates its input`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			authed.addEntry("writer@example.com", "never")

			val body = authed.postFragment("edit-reason", "email=writer%40example.com&reason=Proofreader")
			assertContains(body, "Proofreader")
			assertEquals(
				"Proofreader",
				database().serverDatabase.whiteListQueries.getAll().executeAsList()
					.single { it.email == "writer@example.com" }.reason,
			)

			assertContains(
				authed.postFragment("edit-reason", "email=&reason=Whatever"),
				"Email address is required",
			)
			assertContains(
				authed.postFragment("edit-reason", "email=writer%40example.com&reason=${"x".repeat(40)}"),
				"Reason must be 32 characters or less",
			)
		}
	}

	@Test
	fun `edit-expiry validates its input`(): Unit = runBlocking {
		doStartServer()
		seed()

		login().use { authed ->
			authed.addEntry("writer@example.com", "never")

			assertContains(
				authed.postFragment("edit-expiry", "email="),
				"Email address is required",
			)
			assertContains(
				authed.postFragment("edit-expiry", "email=writer%40example.com&expiryPreset=-5"),
				"Expiry date must be a valid date in the future",
			)
		}
	}

	@Test
	fun `old whitelist url permanently redirects to the allowed users page`(): Unit = runBlocking {
		doStartServer()
		seed()

		HttpClient {
			install(HttpCookies)
			followRedirects = false
		}.use { authed ->
			val loginResponse = authed.post(route("login")) {
				contentType(ContentType.Application.FormUrlEncoded)
				setBody(
					"email=${URLEncoder.encode(email, "UTF-8")}" +
						"&password=${URLEncoder.encode(password, "UTF-8")}"
				)
			}
			assertEquals(HttpStatusCode.Found, loginResponse.status)

			val redirect = authed.get(route("admin/whitelist"))
			assertEquals(HttpStatusCode.MovedPermanently, redirect.status)
			assertEquals("/admin/allowed-users", redirect.headers["Location"])

			val page = authed.get(route("admin/allowed-users"))
			assertEquals(HttpStatusCode.OK, page.status)
			assertContains(page.bodyAsText(), "Allowed Users")
		}
	}

	private companion object {
		const val SOURCE_REASON = "TestSync"
	}
}
