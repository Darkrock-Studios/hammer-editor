package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.COOKIE_SHARE_ACCESS_SESSION
import com.darkrockstudios.apps.hammer.frontend.utils.MAX_UNLOCKED_SHARES
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A private share is unlocked once, into the encrypted share-access session cookie, and the
 * password then stays out of every URL. Each request checks the cookie against the live share.
 */
class PublicStoryShareCookieTest : EndToEndTest() {

	private val testClock = TestClock(Clock.System)
	override val clock: Clock = testClock

	private val userId = 1L
	private val penName = "JaneAuthor"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())
	private val storyPath = "/a/$penName/$segment"

	/** A browser that keeps cookies and shows each redirect instead of following it. */
	private fun browser() = HttpClient {
		install(HttpCookies)
		followRedirects = false
	}

	private fun url(pathAndQuery: String) = route(pathAndQuery.removePrefix("/"))

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(userId, projectNameText).executeAsOne().id

	/** The author, the story, and two scenes, "test content 1" and "test content 2". */
	private fun seedStory() = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "jane@test.com", password = "password123!@#"), database())
		database().serverDatabase.accountQueries.updatePenName(pen_name = penName, id = userId)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId), database())
		for (sceneId in 1..2) {
			E2eTestData.insertEntity(
				userId = userId,
				projectId = projectRowId(),
				entity = E2eTestData.createTestScene(sceneId),
				testDatabase = database(),
				contentEncryptor = encryptor(),
			)
		}
	}

	/** A private share; returns its id. */
	private fun grantShare(password: String, sceneIds: List<Int> = emptyList(), expiresAt: Instant? = null): Long {
		val queries = database().serverDatabase
		val accessId = queries.projectAccessQueries.insertAccessReturningId(
			project_id = projectRowId(),
			access_password = password,
			expires_at = expiresAt,
		).executeAsOne()
		sceneIds.forEach { queries.projectAccessSceneQueries.insertScene(access_id = accessId, scene_id = it) }
		return accessId
	}

	private suspend fun HttpClient.submitPassword(password: String, page: String? = null): HttpResponse =
		post(url(storyPath)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("password=$password" + (page?.let { "&page=$it" } ?: ""))
		}

	private fun shareCookieSet(response: HttpResponse): String? =
		response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
			.firstOrNull { it.startsWith("$COOKIE_SHARE_ACCESS_SESSION=") }

	private fun String.isPasswordForm() = contains("password-form")

	@Test
	fun `the password form unlocks the share and sends the reader to the clean URL`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")
		val browser = browser()

		val response = browser.submitPassword("secret")

		assertEquals(HttpStatusCode.NoContent, response.status)
		assertEquals(storyPath, response.headers[HxResponseHeaders.Redirect])
		val setCookie = assertNotNull(shareCookieSet(response), "the share cookie is set")
		assertTrue(setCookie.contains("Path=/;") || setCookie.endsWith("Path=/"), setCookie)
		assertTrue(setCookie.contains("Max-Age=86400"), setCookie)
		assertTrue(setCookie.contains("HttpOnly"), setCookie)
		assertTrue(setCookie.contains("SameSite=lax", ignoreCase = true), setCookie)
		assertFalse(setCookie.contains("secret"), "the cookie never holds the password")

		val story = browser.get(url(storyPath))
		assertEquals(HttpStatusCode.OK, story.status)
		val body = story.bodyAsText()
		assertTrue(body.contains("test content 1"), "the unlocked browser reads the story")
		assertFalse(body.contains("p=secret"), "no link on the page carries the password")

		assertTrue(client().get(url(storyPath)).bodyAsText().isPasswordForm(), "another browser still gets the form")
	}

	@Test
	fun `the password form keeps the page the reader asked for`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")
		val browser = browser()

		val form = browser.get(url("$storyPath?page=2")).bodyAsText()
		assertTrue(form.isPasswordForm(), form)
		assertTrue(form.contains("""<input type="hidden" name="page" value="2">"""), form)

		val response = browser.submitPassword("secret", page = "2")
		assertEquals("$storyPath?page=2", response.headers[HxResponseHeaders.Redirect])

		val notAPage = browser().submitPassword("secret", page = "-1")
		assertEquals(storyPath, notAPage.headers[HxResponseHeaders.Redirect], "only a positive page number is kept")
	}

	@Test
	fun `a wrong password shows the form again and unlocks nothing`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")
		val browser = browser()

		val response = browser.submitPassword("wrong")

		assertEquals(HttpStatusCode.OK, response.status)
		assertNull(response.headers[HxResponseHeaders.Redirect])
		assertNull(shareCookieSet(response))
		val body = response.bodyAsText()
		assertTrue(body.isPasswordForm() && body.contains("password-error"), body)
		assertTrue(browser.get(url(storyPath)).bodyAsText().isPasswordForm())
	}

	@Test
	fun `an old password link unlocks the share once and redirects to the clean URL`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")
		val browser = browser()

		val response = browser.get(url("$storyPath?p=secret&page=2"))

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("$storyPath?page=2", response.headers[HttpHeaders.Location])
		assertNotNull(shareCookieSet(response))
		val story = browser.get(url("$storyPath?page=2"))
		assertEquals(HttpStatusCode.OK, story.status)
		assertFalse(story.bodyAsText().isPasswordForm())
	}

	@Test
	fun `a wrong password link shows the form with the error`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")

		val response = browser().get(url("$storyPath?p=wrong"))

		assertEquals(HttpStatusCode.OK, response.status)
		assertNull(shareCookieSet(response))
		assertTrue(response.bodyAsText().contains("password-error"))
	}

	@Test
	fun `changing the share's password ends access`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		val accessId = grantShare("secret")
		val browser = browser()
		browser.submitPassword("secret")

		database().executeAsync("UPDATE project_access SET access_password = 'changed' WHERE id = $accessId")

		assertTrue(browser.get(url(storyPath)).bodyAsText().isPasswordForm())
	}

	@Test
	fun `removing the share ends access`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		val accessId = grantShare("secret")
		val browser = browser()
		browser.submitPassword("secret")

		database().serverDatabase.projectAccessQueries.deleteAccessById(accessId, projectRowId()).executeAsOneOrNull()

		assertEquals(HttpStatusCode.NotFound, browser.get(url(storyPath)).status)
	}

	@Test
	fun `a share that expires ends access when it does`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret", expiresAt = testClock.now() + 2.hours)
		val browser = browser()
		val response = browser.submitPassword("secret")
		assertTrue(assertNotNull(shareCookieSet(response)).isNotEmpty())

		testClock.advanceTime(1.hours)
		assertFalse(browser.get(url(storyPath)).bodyAsText().isPasswordForm(), "still open before the share expires")

		testClock.advanceTime(2.hours)
		assertTrue(browser.get(url(storyPath)).bodyAsText().isPasswordForm(), "closed once the share has expired")
	}

	@Test
	fun `an unlocked share lapses after 24 hours`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")
		val browser = browser()
		browser.submitPassword("secret")

		testClock.advanceTime(23.hours)
		assertFalse(browser.get(url(storyPath)).bodyAsText().isPasswordForm(), "still open within the day")

		testClock.advanceTime(2.hours)
		assertTrue(browser.get(url(storyPath)).bodyAsText().isPasswordForm(), "lapsed after 24 hours")
	}

	@Test
	fun `two shares of one story stay apart, and the last one unlocked is read`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("alpha", sceneIds = listOf(1))
		grantShare("beta", sceneIds = listOf(2))
		val browser = browser()

		browser.submitPassword("alpha")
		val alpha = browser.get(url(storyPath))
		val alphaBody = alpha.bodyAsText()
		assertTrue(alphaBody.contains("test content 1") && !alphaBody.contains("test content 2"), alphaBody)

		browser.submitPassword("beta")
		val beta = browser.get(url(storyPath))
		val betaBody = beta.bodyAsText()
		assertTrue(betaBody.contains("test content 2") && !betaBody.contains("test content 1"), betaBody)

		assertNotEquals(alpha.headers[HttpHeaders.ETag], beta.headers[HttpHeaders.ETag], "each share has its own validator")
	}

	@Test
	fun `a forged share cookie opens nothing`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantShare("secret")

		val response = client().get(url(storyPath)) {
			header(HttpHeaders.Cookie, "$COOKIE_SHARE_ACCESS_SESSION=00ff00ff/00ff00ff")
		}

		assertEquals(HttpStatusCode.OK, response.status)
		assertTrue(response.bodyAsText().isPasswordForm())
	}

	@Test
	fun `the share cookie stays inside the browsers' size limit`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		val passwords = (1..MAX_UNLOCKED_SHARES + 1).map { "password-number-$it" }
		passwords.forEach { grantShare(it) }
		val browser = browser()

		passwords.forEach { browser.submitPassword(it) }

		val cookie = assertNotNull(browser.cookies(url(storyPath)).firstOrNull { it.name == COOKIE_SHARE_ACCESS_SESSION })
		assertTrue(cookie.value.length < 4000, "share cookie is ${cookie.value.length} bytes")
		assertFalse(browser.get(url(storyPath)).bodyAsText().isPasswordForm())
	}
}
