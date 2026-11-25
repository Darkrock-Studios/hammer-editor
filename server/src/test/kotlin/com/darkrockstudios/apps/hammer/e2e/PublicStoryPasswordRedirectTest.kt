package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
 * Submitting a private share's password redirects back to the story. When the pen name or the
 * story's name has letters outside ASCII, the Location header must carry them percent-encoded as
 * UTF-8; sent raw, "ë" went out as the Latin-1 byte 0xEB and the browser landed on a 404.
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

	/** Submits the password form the way the page's htmx does, without following the redirect. */
	private suspend fun submitPassword(formBody: String): HttpResponse =
		HttpClient { followRedirects = false }.post(route(storyPath.removePrefix("/"))) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(formBody)
		}

	private fun assertAsciiLocation(response: HttpResponse): String {
		assertEquals(HttpStatusCode.Found, response.status)
		val location = assertNotNull(response.headers[HttpHeaders.Location])
		assertTrue(location.all { it.code < 128 }, "the Location header must be plain ASCII: $location")
		return location
	}

	@Test
	fun `the password redirect keeps non-ASCII names encoded and lands on the story`(): Unit = runBlocking {
		doStartServer()
		seedPrivateShare()

		val location = assertAsciiLocation(submitPassword("password=$password"))
		assertEquals("$storyPath?p=$password", location)

		val story = client().get(route(location.removePrefix("/")))
		assertEquals(HttpStatusCode.OK, story.status)
		assertTrue(story.bodyAsText().contains("test content 1"), "the redirect must land on the story")
	}

	@Test
	fun `a blank password redirects to the encoded story address`(): Unit = runBlocking {
		doStartServer()
		seedPrivateShare()

		val location = assertAsciiLocation(submitPassword("password="))
		assertEquals(storyPath, location)
	}
}
