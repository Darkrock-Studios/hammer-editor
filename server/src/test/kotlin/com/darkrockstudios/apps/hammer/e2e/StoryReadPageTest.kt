package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The author's chapter reader at /story/{project}/read. */
class StoryReadPageTest : WebEndToEndTest() {

	private val email = "jane@test.com"
	private val otherEmail = "sam@test.com"
	private val password = "password123!@#"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val projectSegment = ProjectName.projectSegment(projectNameText, projectUuid.toString())

	private fun userId(email: String = this.email): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(userId(), projectNameText).executeAsOne().id

	private fun readPath(query: String = ""): String = route("story/$projectSegment/read$query")

	/** Two chapters: a group holding two scenes, then a lone top-level scene. */
	private fun seedStory() = runBlocking {
		seedWhitelistedAccount(email, password)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId()), database())
		insert(scene(1, "Part One", "", order = 0, type = ApiSceneType.Group))
		insert(scene(2, "Arrival", "The train came in late.", order = 0, path = listOf(0, 1)))
		insert(scene(3, "The Inn", "Rooms were scarce.", order = 1, path = listOf(0, 1)))
		insert(scene(4, "Epilogue", "Years later, the town remembered.", order = 1))
	}

	private fun scene(
		id: Int,
		name: String,
		content: String,
		order: Int,
		path: List<Int> = listOf(0),
		type: ApiSceneType = ApiSceneType.Scene,
	) = ApiProjectEntity.SceneEntity(
		id = id,
		sceneType = type,
		order = order,
		name = name,
		path = path,
		content = content,
	)

	private fun insert(entity: ApiProjectEntity) =
		E2eTestData.insertEntity(userId(), projectRowId(), entity, database(), encryptor())

	/** The page with Mustache's attribute escaping of `=` undone, so links read as written. */
	private suspend fun HttpResponse.unescapedBody(): String = bodyAsText().replace("&#61;", "=")

	@Test
	fun `a signed-out visitor is sent to log in`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		val anonymous = HttpClient { followRedirects = false }
		val response = try {
			anonymous.get(readPath())
		} finally {
			anonymous.close()
		}

		assertEquals(HttpStatusCode.Found, response.status)
		assertEquals("/login", response.headers["Location"])
	}

	@Test
	fun `another author cannot read the story`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		seedWhitelistedAccount(otherEmail, password)

		login(otherEmail, password).use { other ->
			assertEquals(HttpStatusCode.NotFound, other.get(readPath()).status)
		}
	}

	@Test
	fun `the first chapter is shown by default with its scenes in order`(): Unit = runBlocking {
		doStartServer()
		seedStory()

		login(email, password).use { author ->
			val response = author.get(readPath())
			val body = response.unescapedBody()

			assertEquals(HttpStatusCode.OK, response.status)
			assertTrue(body.contains("Part One"), body)
			assertTrue(body.indexOf("The train came in late.") < body.indexOf("Rooms were scarce."))
			assertFalse(body.contains("Years later, the town remembered."), "only one chapter per page")
			assertTrue(body.contains("Chapter 1 of 2"))
			assertTrue(body.contains("""href="/story/$projectSegment/read?chapter=2" class="reader-nav__link"""))
			assertFalse(body.contains("data-reader-prev"), "the first chapter has nothing before it")
		}
	}

	@Test
	fun `a later chapter shows only its own scenes and ends the story`(): Unit = runBlocking {
		doStartServer()
		seedStory()

		login(email, password).use { author ->
			val body = author.get(readPath("?chapter=2")).unescapedBody()

			assertTrue(body.contains("Years later, the town remembered."), body)
			assertFalse(body.contains("The train came in late."))
			assertTrue(body.contains("Chapter 2 of 2"))
			assertTrue(body.contains("""href="/story/$projectSegment/read?chapter=1" class="reader-nav__link"""))
			assertFalse(body.contains("data-reader-next"), "the last chapter has nothing after it")
			assertTrue(body.contains("End of story"))
		}
	}

	@Test
	fun `an out of range chapter shows the nearest one`(): Unit = runBlocking {
		doStartServer()
		seedStory()

		login(email, password).use { author ->
			assertTrue(author.get(readPath("?chapter=99")).bodyAsText().contains("Chapter 2 of 2"))
			assertTrue(author.get(readPath("?chapter=-4")).bodyAsText().contains("Chapter 1 of 2"))
			assertTrue(author.get(readPath("?chapter=abc")).bodyAsText().contains("Chapter 1 of 2"))
		}
	}

	@Test
	fun `scene names are escaped`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		insert(scene(5, "<script>alert(1)</script>", "Prose.", order = 2))

		login(email, password).use { author ->
			val body = author.get(readPath("?chapter=3")).bodyAsText()

			assertFalse(body.contains("<script>alert(1)</script>"), body)
			assertTrue(body.contains("&lt;script&gt;"))
		}
	}

	@Test
	fun `a story with no scenes says there is nothing to read`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount(email, password)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId()), database())

		login(email, password).use { author ->
			val response = author.get(readPath())

			assertEquals(HttpStatusCode.OK, response.status)
			assertTrue(response.bodyAsText().contains("There is nothing to read yet"))
		}
	}
}
