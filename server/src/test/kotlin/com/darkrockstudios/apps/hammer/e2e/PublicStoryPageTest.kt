package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
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
import kotlin.uuid.Uuid

/**
 * The public reader page: its HTTP validator, and the rule that only a publicly-reachable story's
 * rendered prose may be written to the disk cache.
 */
class PublicStoryPageTest : EndToEndTest() {

	private val userId = 1L
	private val penName = "JaneAuthor"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()

	private fun storyPath(password: String? = null): String {
		val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())
		val query = if (password != null) "?p=$password" else ""
		return route("a/$penName/$segment$query")
	}

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(userId, projectNameText).executeAsOne().id

	private fun seedStory() = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "jane@test.com", password = "password123!@#"), database())
		database().serverDatabase.accountQueries.updatePenName(pen_name = penName, id = userId)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId), database())
		E2eTestData.insertEntity(
			userId = userId,
			projectId = projectRowId(),
			entity = E2eTestData.createTestScene(1),
			testDatabase = database(),
			contentEncryptor = encryptor(),
		)
	}

	private fun grantAccess(password: String?) {
		database().serverDatabase.projectAccessQueries.insertAccess(
			project_id = projectRowId(),
			access_password = password,
			expires_at = null,
		)
	}

	@Test
	fun `a published story is served with a revalidation validator`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val response = client().get(storyPath())

		assertEquals(HttpStatusCode.OK, response.status)
		assertTrue(response.bodyAsText().contains("test content 1"), "the story prose should render")
		val etag = response.headers[HttpHeaders.ETag]
		assertNotNull(etag, "the page should carry a validator")
		assertTrue(etag.startsWith("W/\""), "the validator should be weak, was $etag")
		assertEquals("private, no-cache", response.headers[HttpHeaders.CacheControl])
	}

	@Test
	fun `a reader holding the validator is answered 304`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val first = client().get(storyPath())
		val etag = assertNotNull(first.headers[HttpHeaders.ETag])
		val second = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, second.status)
		assertTrue(second.bodyAsText().isEmpty(), "a 304 carries no body")
	}

	@Test
	fun `editing the story changes the validator`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val before = assertNotNull(client().get(storyPath()).headers[HttpHeaders.ETag])

		// Sync an edit the way an upload would: new content, and so a new stored hash.
		val edited = E2eTestData.createTestScene(1).copy(content = "a freshly written chapter")
		database().serverDatabase.storyEntityQueries.deleteEntity(userId, projectRowId(), 1L)
		E2eTestData.insertEntity(userId, projectRowId(), edited, database(), encryptor())

		val response = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "an edited story must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("a freshly written chapter"))
	}

	@Test
	fun `a published story is cached to disk`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		client().get(storyPath())

		assertEquals(1, cachedFiles("story-html").size, "a public render should be cached")
	}

	@Test
	fun `a password-protected story is never written to disk`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret")

		val response = client().get(storyPath(password = "secret"))

		assertEquals(HttpStatusCode.OK, response.status)
		assertTrue(response.bodyAsText().contains("test content 1"), "the reader still gets the story")
		// Scene content is encrypted at rest; a private share's decrypted prose must not land
		// in a plaintext cache file.
		assertEquals(
			emptyList(),
			cachedFiles("story-html"),
			"a private share must leave no rendered prose on disk"
		)
	}

	@Test
	fun `a story with no access is not found`(): Unit = runBlocking {
		doStartServer()
		seedStory() // never published, never shared

		val response = client().get(storyPath())

		assertEquals(HttpStatusCode.NotFound, response.status)
		assertEquals(emptyList(), cachedFiles("story-html"))
	}
}
