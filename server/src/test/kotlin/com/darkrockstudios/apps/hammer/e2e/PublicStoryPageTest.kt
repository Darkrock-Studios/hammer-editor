package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.utilities.DiskCache
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

	private fun grantAccess(password: String?, sceneIds: List<Int> = emptyList()) {
		val queries = database().serverDatabase
		queries.projectAccessQueries.insertAccess(
			project_id = projectRowId(),
			access_password = password,
			expires_at = null,
		)
		if (sceneIds.isNotEmpty()) {
			val accessId = queries.projectAccessQueries.getAllAccessForProject(projectRowId())
				.executeAsList().last().id
			sceneIds.forEach { sceneId ->
				queries.projectAccessSceneQueries.insertScene(access_id = accessId, scene_id = sceneId)
			}
		}
	}

	private fun seedSecondScene() = runBlocking {
		E2eTestData.insertEntity(
			userId = userId,
			projectId = projectRowId(),
			entity = E2eTestData.createTestScene(2),
			testDatabase = database(),
			contentEncryptor = encryptor(),
		)
	}

	private fun seedProjectData(content: String) {
		database().serverDatabase.projectDataQueries.upsert(
			userId = userId,
			projectId = projectRowId(),
			content = content,
			hash = "test-hash",
			updatedAt = kotlin.time.Clock.System.now(),
		)
	}

	@Test
	fun `a story with a declared language is served with that language`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		seedProjectData("""{"language":"fr"}""")
		// JSON-LD is only emitted for indexable pages, which require a community author.
		database().serverDatabase.accountQueries.updateCommunityMember(community_member = true, id = userId)

		val body = client().get(storyPath()).bodyAsText()

		assertTrue(body.contains("<html lang=\"fr\""), "html lang should be the story's language")
		assertTrue(body.contains("\"inLanguage\":\"fr\""), "the Article JSON-LD should carry inLanguage")
	}

	@Test
	fun `a story without a declared language keeps the viewer locale`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		seedProjectData("""{"authorName":"Jane"}""")

		val body = client().get(storyPath()).bodyAsText()

		assertTrue(body.contains("<html lang=\"en\""), "html lang should fall back to the viewer locale")
		assertTrue(!body.contains("inLanguage"), "no inLanguage without a declared language")
	}

	@Test
	fun `declaring a language changes the validator`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val before = assertNotNull(client().get(storyPath()).headers[HttpHeaders.ETag])

		seedProjectData("""{"language":"fr"}""")
		val response = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status, "a language change must not answer 304")
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
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

		assertEquals(1, cachedFiles(DiskCache.STORY_HTML).size, "a public render should be cached")
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
			cachedFiles(DiskCache.STORY_HTML),
			"a private share must leave no rendered prose on disk"
		)
	}

	@Test
	fun `a story with no access is not found`(): Unit = runBlocking {
		doStartServer()
		seedStory() // never published, never shared

		val response = client().get(storyPath())

		assertEquals(HttpStatusCode.NotFound, response.status)
		assertEquals(emptyList(), cachedFiles(DiskCache.STORY_HTML))
	}

	@Test
	fun `a scene-limited share renders only the selected scenes`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		seedSecondScene()
		grantAccess(password = "secret", sceneIds = listOf(1))

		val body = client().get(storyPath(password = "secret")).bodyAsText()

		assertTrue(body.contains("test content 1"), "the selected scene should render")
		assertTrue(!body.contains("test content 2"), "an unselected scene must not leak into the share")
	}

	@Test
	fun `a deleted selected scene silently drops out of a limited share`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		seedSecondScene()
		grantAccess(password = "secret", sceneIds = listOf(1, 2))

		database().serverDatabase.storyEntityQueries.deleteEntity(userId, projectRowId(), 2L)
		val response = client().get(storyPath(password = "secret"))

		assertEquals(HttpStatusCode.OK, response.status)
		val body = response.bodyAsText()
		assertTrue(body.contains("test content 1"))
		assertTrue(!body.contains("test content 2"))
	}

	@Test
	fun `a limited share whose every scene is gone is not found`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret", sceneIds = listOf(1))

		database().serverDatabase.storyEntityQueries.deleteEntity(userId, projectRowId(), 1L)
		val response = client().get(storyPath(password = "secret"))

		assertEquals(HttpStatusCode.NotFound, response.status)
	}

	@Test
	fun `editing an unselected scene keeps a limited share's validator`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		seedSecondScene()
		grantAccess(password = "secret", sceneIds = listOf(1))

		val before = assertNotNull(client().get(storyPath(password = "secret")).headers[HttpHeaders.ETag])

		val edited = E2eTestData.createTestScene(2).copy(content = "an edit outside the share")
		database().serverDatabase.storyEntityQueries.deleteEntity(userId, projectRowId(), 2L)
		E2eTestData.insertEntity(userId, projectRowId(), edited, database(), encryptor())

		val response = client().get(storyPath(password = "secret")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.NotModified, response.status, "an unselected edit must not invalidate the share")
	}

	@Test
	fun `editing a selected scene changes a limited share's validator`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		seedSecondScene()
		grantAccess(password = "secret", sceneIds = listOf(1))

		val before = assertNotNull(client().get(storyPath(password = "secret")).headers[HttpHeaders.ETag])

		val edited = E2eTestData.createTestScene(1).copy(content = "a freshly written chapter")
		database().serverDatabase.storyEntityQueries.deleteEntity(userId, projectRowId(), 1L)
		E2eTestData.insertEntity(userId, projectRowId(), edited, database(), encryptor())

		val response = client().get(storyPath(password = "secret")) { header(HttpHeaders.IfNoneMatch, before) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertNotEquals(before, response.headers[HttpHeaders.ETag])
		assertTrue(response.bodyAsText().contains("a freshly written chapter"))
	}
}
