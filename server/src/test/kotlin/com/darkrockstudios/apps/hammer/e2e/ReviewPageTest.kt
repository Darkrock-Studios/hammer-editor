package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.review.ReviewStatus
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ReviewPageTest : EndToEndTest() {

	private val userId = 1L
	private val plainToken = "test-review-token-0123456789abcdef"

	private fun seedReview(status: ReviewStatus): Long = runBlocking {
		val account = TestAccount(email = "author@test.com", password = "password123!@#")
		E2eTestData.createAccount(account, database())
		E2eTestData.createProject(
			TestProject(name = "Insurgency", uuid = Uuid.random(), userId = userId),
			database(),
		)
		val db = database().serverDatabase
		val projectRowId = db.projectQueries.findProjectByName(userId, "Insurgency").executeAsOne().id

		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret
		val hashedToken = tokenHasher().hashToken(plainToken)

		db.reviewRequestQueries.createRequest(
			userId = userId,
			projectId = projectRowId,
			token = hashedToken,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = "Please focus on pacing in the middle scenes.",
			status = status.toStringId(),
			expires = null,
		)
		val requestId = db.reviewRequestQueries.getRequestByToken(hashedToken).executeAsOne().id

		db.reviewSceneQueries.createScene(
			reviewRequestId = requestId,
			sceneId = 1,
			draftId = 2,
			sceneName = "Ship Arrival",
			sceneOrder = 0,
			snapshotContent = encryptor().encrypt(
				"Weary valves clunked shut after an *extended* burn.",
				cipherSecret,
			),
		)
		requestId
	}

	@Test
	fun `review page renders manuscript for a valid token`(): Unit = runBlocking {
		doStartServer()
		seedReview(ReviewStatus.SENT)

		val response = client().get(route("review/$plainToken"))
		val body = response.bodyAsText()

		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(body, "Insurgency")
		assertContains(body, "Ship Arrival")
		// Snapshot content rides along in the JSON data island as raw markdown
		assertContains(body, "Weary valves clunked shut")
		assertContains(body, "reviewer@example.com")
		assertContains(body, "Please focus on pacing")

		val statusAfter = database().serverDatabase.reviewRequestQueries
			.getRequestByToken(tokenHasher().hashToken(plainToken))
			.executeAsOne().status
		assertEquals(ReviewStatus.OPENED.toStringId(), statusAfter)
	}

	@Test
	fun `review page rejects an unknown token`(): Unit = runBlocking {
		doStartServer()
		// Seed an account so the server isn't redirecting everything to first-run /setup
		seedReview(ReviewStatus.SENT)

		val response = client().get(route("review/not-a-real-token"))

		assertEquals(HttpStatusCode.Gone, response.status)
		assertContains(response.bodyAsText(), "review-error")
	}

	@Test
	fun `review page rejects a revoked token`(): Unit = runBlocking {
		doStartServer()
		seedReview(ReviewStatus.CANCELED)

		val response = client().get(route("review/$plainToken"))

		assertEquals(HttpStatusCode.Gone, response.status)
		assertContains(response.bodyAsText(), "review-error")
	}
}
