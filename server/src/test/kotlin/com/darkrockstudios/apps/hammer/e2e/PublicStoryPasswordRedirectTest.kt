package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.htmx.HxResponseHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unlocking a private share redirects back to the story: the password form through HX-Redirect,
 * an old `?p=` link through Location. When the pen name or the story's name has letters outside
 * ASCII, the header must carry them percent-encoded as UTF-8; sent raw, "ë" went out as the
 * Latin-1 byte 0xEB and the browser landed on a 404.
 */
class PublicStoryPasswordRedirectTest : EndToEndTest() {

	private val userId = 1L
	private val penName = "Zoë"
	private val projectNameText = "Café Noir"
	private val projectUuid = Uuid.random()
	private val password = "secret"

	/** The story's address as the reader page links to it: every segment UTF-8 percent-encoded. */
	private val storyPath =
		"/a/${penName.encodeURLPathPart()}/${ProjectName.projectSegment(projectNameText, projectUuid.toString())}"

	private fun seedPrivateShare() = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "zoe@test.com", password = "password123!@#"), database())
		database().serverDatabase.accountQueries.updatePenName(pen_name = penName, id = userId)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId), database())
		val projectRowId = database().serverDatabase.projectQueries
			.findProjectByName(userId, projectNameText).executeAsOne().id
		E2eTestData.insertEntity(
			userId = userId,
			projectId = projectRowId,
			entity = E2eTestData.createTestScene(1),
			testDatabase = database(),
			contentEncryptor = encryptor(),
		)
		database().serverDatabase.projectAccessQueries.insertAccessReturningId(
			project_id = projectRowId,
			access_password = password,
			expires_at = null,
		).executeAsOne()
	}

	/** A browser that keeps cookies and, like htmx, shows each redirect instead of following it. */
	private val browser = HttpClient {
		install(HttpCookies)
		followRedirects = false
	}

	private fun assertAscii(header: String?, name: String): String {
		val value = assertNotNull(header, "$name header")
		assertTrue(value.all { it.code < 128 }, "the $name header must be plain ASCII: $value")
		return value
	}

	@Test
	fun `the password form redirects to the encoded story address and lands on the story`(): Unit = runBlocking {
		doStartServer()
		seedPrivateShare()

		val response = browser.post(route(storyPath.removePrefix("/"))) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("password=$password")
		}

		val target = assertAscii(response.headers[HxResponseHeaders.Redirect], "HX-Redirect")
		assertEquals(storyPath, target)

		val story = browser.get(route(target.removePrefix("/")))
		assertEquals(HttpStatusCode.OK, story.status)
		assertTrue(story.bodyAsText().contains("test content 1"), "the redirect must land on the story")
	}

	@Test
	fun `an old password link redirects to the encoded story address`(): Unit = runBlocking {
		doStartServer()
		seedPrivateShare()

		val response = browser.get(route("${storyPath.removePrefix("/")}?p=$password&page=1"))

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("$storyPath?page=1", assertAscii(response.headers[HttpHeaders.Location], "Location"))
	}
}
