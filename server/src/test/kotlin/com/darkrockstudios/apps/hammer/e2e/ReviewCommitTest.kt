package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.review.ReviewStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlin.uuid.Uuid

class ReviewCommitTest : EndToEndTest() {

	private val userId = 1L
	private val email = "author@test.com"
	private val password = "password123!@#"
	private val snapshot = "It flagged the planet as non-viable and moved on."
	private val plainToken = "commit-test-token-0123456789abcdef"

	private data class Seeded(
		val projectRowId: Long,
		val requestId: Long,
		val suggestionId: Long,
		val storySegment: String,
	)

	private fun seed(
		currentSceneContent: String = snapshot,
		status: ReviewStatus = ReviewStatus.SUBMITTED,
	): Seeded = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password), database())
		database().serverDatabase.whiteListQueries.addToWhiteList(
			email,
			Clock.System.now(),
			"Test author",
			null,
		)
		E2eTestData.createProject(TestProject("Insurgency", Uuid.random(), userId), database())
		val db = database().serverDatabase
		val insurgency = db.projectQueries.findProjectByName(userId, "Insurgency").executeAsOne()
		val projectRowId = insurgency.id
		val storySegment = ProjectName.projectSegment("Insurgency", insurgency.uuid)
		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret

		E2eTestData.insertEntity(
			userId, projectRowId,
			ApiProjectEntity.SceneEntity(
				id = 1,
				sceneType = ApiSceneType.Scene,
				order = 0,
				name = "Ship Arrival",
				path = listOf(0),
				content = currentSceneContent,
			),
			database(), encryptor(),
		)

		val hashedToken = tokenHasher().hashToken(plainToken)
		db.reviewRequestQueries.createRequest(
			userId = userId,
			projectId = projectRowId,
			token = hashedToken,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = null,
			status = status.toStringId(),
			expires = null,
		)
		val requestId = db.reviewRequestQueries.getRequestByToken(hashedToken).executeAsOne().id

		db.reviewSceneQueries.createScene(
			reviewRequestId = requestId,
			sceneId = 1,
			draftId = 99,
			sceneName = "Ship Arrival",
			sceneOrder = 0,
			snapshotContent = encryptor().encrypt(snapshot, cipherSecret),
			cipher = encryptor().cipherName(),
		)
		val sceneRowId = db.reviewSceneQueries.getScenesForRequest(requestId).executeAsOne().id

		val suggestionId = db.reviewSuggestionQueries.createSuggestion(
			reviewSceneId = sceneRowId,
			type = "reword",
			paragraph = 0,
			startOffset = 25, // "non-viable"
			endOffset = 35,
			originalText = "non-viable",
			replacementText = "nonviable",
			reason = null,
			status = "pending",
		).executeAsOne()

		Seeded(projectRowId, requestId, suggestionId, storySegment)
	}

	private suspend fun login(followRedirects: Boolean = true): HttpClient {
		val authed = HttpClient {
			install(HttpCookies)
			this.followRedirects = followRedirects
		}
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		// Success redirects to the dashboard; a failed login re-renders the form with 200
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	/** The decrypted scene entity JSON (the whole entity, content included). */
	private fun decryptedSceneContent(projectRowId: Long): String = runBlocking {
		val db = database().serverDatabase
		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret
		val row = db.storyEntityQueries.getEntity(userId, projectRowId, 1L).executeAsOne()
		encryptor().decrypt(row.content, cipherSecret)
	}

	@Test
	fun `author page renders the submitted review`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed()

		login().use { authed ->
			val page = authed.get(route("story/${seeded.storySegment}/reviews/${seeded.requestId}"))
			assertEquals(HttpStatusCode.OK, page.status)
			val body = page.bodyAsText()
			assertContains(body, "review-data")
			assertContains(body, "\"mode\":\"author\"")
			assertContains(body, "\"canDecide\":true")
		}
	}

	@Test
	fun `a review does not render or commit under another of the user's projects`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed()
		// A second project owned by the same user; the review belongs to Insurgency.
		E2eTestData.createProject(TestProject("Decoy", Uuid.random(), userId), database())
		val decoy = database().serverDatabase.projectQueries
			.findProjectByName(userId, "Decoy").executeAsOne()
		val decoySegment = ProjectName.projectSegment("Decoy", decoy.uuid)

		login().use { authed ->
			val page = authed.get(route("story/$decoySegment/reviews/${seeded.requestId}"))
			assertEquals(HttpStatusCode.Gone, page.status)

			val commit = authed.post(route("story/$decoySegment/reviews/${seeded.requestId}/commit"))
			assertEquals(HttpStatusCode.NotFound, commit.status)

			// Still reachable under its real project
			val real = authed.get(route("story/${seeded.storySegment}/reviews/${seeded.requestId}"))
			assertEquals(HttpStatusCode.OK, real.status)
		}
	}

	@Test
	fun `accept and commit updates the clean scene and resolves the request`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed()

		login().use { authed ->
			val statusResponse = authed.post(
				route("story/${seeded.storySegment}/reviews/${seeded.requestId}/suggestions/${seeded.suggestionId}/status")
			) {
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("status=accepted")
			}
			assertEquals(HttpStatusCode.OK, statusResponse.status)
			assertContains(statusResponse.bodyAsText(), "\"status\":\"accepted\"")

			val commitResponse = authed.post(route("story/${seeded.storySegment}/reviews/${seeded.requestId}/commit"))
			assertEquals(HttpStatusCode.OK, commitResponse.status)
			val commitBody = commitResponse.bodyAsText()
			assertContains(commitBody, "\"outcome\":\"applied\"")
			assertContains(commitBody, "Editorial Review My agent")
		}

		val db = database().serverDatabase
		val requestStatus = db.reviewRequestQueries
			.getRequest(seeded.requestId, userId).executeAsOne().status
		assertEquals(ReviewStatus.RESOLVED.toStringId(), requestStatus)

		val revised = decryptedSceneContent(seeded.projectRowId)
		assertContains(revised, "nonviable")
		assertFalse(revised.contains("non-viable"))

		// The reviewed draft was minted as a real scene_draft entity above the scene's ID
		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret
		val draftRow = db.storyEntityQueries.getEntity(userId, seeded.projectRowId, 2L).executeAsOne()
		assertEquals("scene_draft", draftRow.type)
		val draftJson = encryptor().decrypt(draftRow.content, cipherSecret)
		assertContains(draftJson, "nonviable")
		assertContains(draftJson, "Editorial Review My agent")

		// A second commit must fail: the review is already resolved
		login().use { authed ->
			val again = authed.post(route("story/${seeded.storySegment}/reviews/${seeded.requestId}/commit"))
			assertEquals(HttpStatusCode.Conflict, again.status)
		}
	}

	@Test
	fun `commit on a diverged scene mints the draft but leaves the scene alone`(): Unit = runBlocking {
		doStartServer()
		val diverged = "The author kept writing and this scene moved on."
		val seeded = seed(currentSceneContent = diverged)

		login().use { authed ->
			val statusResponse = authed.post(
				route("story/${seeded.storySegment}/reviews/${seeded.requestId}/suggestions/${seeded.suggestionId}/status")
			) {
				contentType(ContentType.Application.FormUrlEncoded)
				setBody("status=accepted")
			}
			assertEquals(HttpStatusCode.OK, statusResponse.status)

			val commitResponse = authed.post(route("story/${seeded.storySegment}/reviews/${seeded.requestId}/commit"))
			assertEquals(HttpStatusCode.OK, commitResponse.status)
			assertContains(commitResponse.bodyAsText(), "\"outcome\":\"diverged\"")
		}

		val sceneJson = decryptedSceneContent(seeded.projectRowId)
		assertContains(sceneJson, diverged)
		assertFalse(sceneJson.contains("nonviable"))

		// The draft still carries the revised snapshot text
		val db = database().serverDatabase
		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret
		val draftRow = db.storyEntityQueries.getEntity(userId, seeded.projectRowId, 2L).executeAsOne()
		assertEquals("scene_draft", draftRow.type)
		assertContains(encryptor().decrypt(draftRow.content, cipherSecret), "nonviable")
	}

	@Test
	fun `author following the reviewer link is forwarded to their review page`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed()

		login(followRedirects = false).use { authed ->
			val response = authed.get(route("review/$plainToken"))
			assertEquals(HttpStatusCode.Found, response.status)
			assertEquals(
				"/story/${seeded.storySegment}/reviews/${seeded.requestId}",
				response.headers[HttpHeaders.Location],
			)
		}
	}

	@Test
	fun `author following an unsubmitted reviewer link lands on the story page unopened`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed(status = ReviewStatus.SENT)

		login(followRedirects = false).use { authed ->
			val response = authed.get(route("review/$plainToken"))
			assertEquals(HttpStatusCode.Found, response.status)
			assertEquals("/story/${seeded.storySegment}", response.headers[HttpHeaders.Location])
		}

		// The author's own visit must not flip the request to opened
		val status = database().serverDatabase.reviewRequestQueries
			.getRequest(seeded.requestId, userId).executeAsOne().status
		assertEquals(ReviewStatus.SENT.toStringId(), status)
	}

	@Test
	fun `story page shows the editor's scene progress while the review is out`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed(status = ReviewStatus.IN_PROGRESS)
		val db = database().serverDatabase
		val sceneRowId = db.reviewSceneQueries.getScenesForRequest(seeded.requestId).executeAsOne().id
		db.reviewSceneQueries.setReviewerDone(true, sceneRowId)

		login().use { authed ->
			val page = authed.get(route("story/${seeded.storySegment}"))
			assertEquals(HttpStatusCode.OK, page.status)
			val body = page.bodyAsText()
			assertContains(body, "1 of 1 scenes read")
			assertContains(body, "review-card__progress")
		}
	}

	@Test
	fun `author endpoints require a logged-in session`(): Unit = runBlocking {
		doStartServer()
		val seeded = seed()

		HttpClient { followRedirects = false }.use { anon ->
			val page = anon.get(route("story/${seeded.storySegment}/reviews/${seeded.requestId}"))
			assertEquals(HttpStatusCode.Found, page.status)

			val commit = anon.post(route("story/${seeded.storySegment}/reviews/${seeded.requestId}/commit"))
			assertTrue(commit.status.value == 302 || commit.status.value == 401)
		}

		val status = database().serverDatabase.reviewRequestQueries
			.getRequest(seeded.requestId, userId).executeAsOne().status
		assertEquals(ReviewStatus.SUBMITTED.toStringId(), status)
	}
}
