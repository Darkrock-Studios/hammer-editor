package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.SWAP_ERROR_HEADER
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class StoryKudosTest : WebEndToEndTest() {

	private val authorEmail = "jane@test.com"
	private val readerEmail = "reader@test.com"
	private val password = "password123!@#"
	private val penName = "JaneAuthor"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())

	private fun storyPath(query: String = ""): String = route("a/$penName/$segment$query")
	private fun kudosPath(): String = route("a/$penName/$segment/kudos")

	private fun accountId(email: String): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(accountId(authorEmail), projectNameText)
			.executeAsOne().id

	private fun seedStory() = runBlocking {
		seedWhitelistedAccount(authorEmail, password, penName = penName)
		seedWhitelistedAccount(readerEmail, password)
		val authorId = accountId(authorEmail)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = authorId), database())
		E2eTestData.insertEntity(
			userId = authorId,
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

	private suspend fun HttpClient.getFragment(): HttpResponse =
		get(kudosPath()) { header("HX-Request", "true") }

	private suspend fun HttpClient.postPicks(vararg kinds: String): HttpResponse =
		post(kudosPath()) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(kinds.joinToString("&") { "kind=$it" })
		}

	private fun savedPicks(): Set<String> =
		database().serverDatabase.storyKudosQueries
			.kindsForUser(projectRowId(), accountId(readerEmail))
			.executeAsList().toSet()

	@Test
	fun `a public story page loads the kudos section but a private share does not`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret")
		val privateShare = client().get(storyPath("?p=secret")).bodyAsText()
		assertContains(privateShare, "story-content")
		assertFalse(privateShare.contains("story-kudos-slot"))

		grantAccess(password = null)
		assertContains(client().get(storyPath()).bodyAsText(), "story-kudos-slot")
	}

	@Test
	fun `a signed-out reader sees disabled chips and a sign-in prompt`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val response = client().getFragment()

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
		val body = response.bodyAsText()
		assertContains(body, "story-kudos__signin")
		assertContains(body, "fieldset class=\"story-kudos__group\" disabled")
	}

	@Test
	fun `a signed-in reader's picks are saved and rendered checked`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		login(readerEmail, password).use { reader ->
			val response = reader.postPicks("prose", "moved_me")

			assertEquals(HttpStatusCode.OK, response.status)
			val body = response.bodyAsText()
			assertContains(body, "value=\"prose\" checked")
			assertContains(body, "value=\"moved_me\" checked")
			assertEquals(setOf("prose", "moved_me"), savedPicks())

			reader.postPicks("plot")
			assertEquals(setOf("plot"), savedPicks())
		}
	}

	@Test
	fun `once four craft chips are picked the rest are locked`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		login(readerEmail, password).use { reader ->
			val body = reader.postPicks("prose", "plot", "pacing", "ending").bodyAsText()

			assertContains(body, "value=\"voice\" disabled")
			assertContains(body, "value=\"prose\" checked")
			assertFalse(body.contains("value=\"prose\" checked disabled"))
			assertFalse(body.contains("value=\"moved_me\" disabled"))
		}
	}

	@Test
	fun `over-cap picks are rejected and leave the saved picks alone`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		login(readerEmail, password).use { reader ->
			reader.postPicks("prose")
			val response = reader.postPicks("prose", "voice", "plot", "pacing", "ending")

			assertEquals(HttpStatusCode.BadRequest, response.status)
			assertEquals(setOf("prose"), savedPicks())
			// The rejected state is replaced by the saved one, with an error toast.
			val body = response.bodyAsText()
			assertContains(body, "value=\"prose\" checked")
			assertFalse(body.contains("value=\"voice\" checked"))
			assertContains(body, "toast-error")
			assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		}
	}

	@Test
	fun `a signed-out post saves nothing and re-renders the sign-in prompt`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val body = client().postPicks("prose").bodyAsText()

		assertContains(body, "story-kudos__signin")
		assertContains(body, "toast-info")
		assertFalse(body.contains("value=\"prose\" checked"))
		assertEquals(0L, database().serverDatabase.storyKudosQueries.giverCountForProject(projectRowId(), listOf("prose")).executeAsOne())
	}

	@Test
	fun `the author sees a note instead of chips and cannot kudo their own story`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		login(authorEmail, password).use { author ->
			val fragment = author.getFragment().bodyAsText()
			assertContains(fragment, "story-kudos__note")
			assertFalse(fragment.contains("story-kudos__form"))

			assertEquals(HttpStatusCode.Forbidden, author.postPicks("prose").status)
		}
	}

	@Test
	fun `private shares and opted-out stories serve no kudos`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret")

		assertEquals(HttpStatusCode.NoContent, client().getFragment().status)

		grantAccess(password = null)
		database().serverDatabase.storyKudosQueries.optOut(projectRowId())

		assertEquals(HttpStatusCode.NoContent, client().getFragment().status)
		login(readerEmail, password).use { reader ->
			val response = reader.postPicks("prose")
			assertEquals(HttpStatusCode.Conflict, response.status)
			assertContains(response.bodyAsText(), "fieldset class=\"story-kudos__group\" disabled")
			assertEquals(emptySet(), savedPicks())
		}
	}

	@Test
	fun `publishing swaps in the kudos panel and a single giver reads singular`(): Unit = runBlocking {
		doStartServer()
		seedStory()

		login(authorEmail, password).use { author ->
			val unpublished = author.get(route("story/$segment")).bodyAsText()
			assertContains(unpublished, "id=\"kudos-panel\" hidden")

			val publish = author.post(route("story/$segment/publish")) { header("HX-Request", "true") }
			val body = publish.bodyAsText()
			assertContains(body, "id=\"kudos-panel\" hx-swap-oob=\"true\"")
			assertContains(body, "Let readers leave kudos")
		}

		login(readerEmail, password).use { it.postPicks("prose") }
		login(authorEmail, password).use { author ->
			assertContains(author.get(route("story/$segment")).bodyAsText(), "reader left kudos")
		}
	}

	@Test
	fun `the author can turn kudos off and back on from the story page`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		val queries = database().serverDatabase.storyKudosQueries

		login(authorEmail, password).use { author ->
			assertContains(author.get(route("story/$segment")).bodyAsText(), "id=\"kudos-panel\"")

			val off = author.post(route("story/$segment/kudos-enabled")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("")
			}
			assertEquals(HttpStatusCode.OK, off.status)
			assertTrue(queries.isOptedOut(projectRowId()).executeAsOne())

			author.post(route("story/$segment/kudos-enabled")) {
				header("HX-Request", "true")
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("enabled=true")
			}
			assertFalse(queries.isOptedOut(projectRowId()).executeAsOne())
		}
	}
}
