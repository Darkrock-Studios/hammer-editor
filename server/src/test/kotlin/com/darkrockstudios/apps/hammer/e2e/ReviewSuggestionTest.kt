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
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ReviewSuggestionTest : EndToEndTest() {

	private val userId = 1L
	private val plainToken = "suggestion-token-0123456789abcdefXY"

	private fun seed(): Long = runBlocking {
		E2eTestData.createAccount(TestAccount("author@test.com", "password123!@#"), database())
		E2eTestData.createProject(TestProject("Insurgency", Uuid.random(), userId), database())
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
			note = null,
			status = ReviewStatus.OPENED.toStringId(),
			expires = null,
		)
		val requestId = db.reviewRequestQueries.getRequestByToken(hashedToken).executeAsOne().id

		db.reviewSceneQueries.createScene(
			reviewRequestId = requestId,
			sceneId = 1,
			draftId = 2,
			sceneName = "Ship Arrival",
			sceneOrder = 0,
			// "It flagged the planet as non-viable and moved on."
			snapshotContent = encryptor().encrypt(
				"It flagged the planet as non-viable and moved on.",
				cipherSecret,
			),
		)
		db.reviewSceneQueries.getScenesForRequest(requestId).executeAsOne().id
	}

	private suspend fun postSuggestion(reviewSceneId: Long, params: Map<String, String>) =
		client().post(route("review/$plainToken/suggestions")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				(params + ("reviewSceneId" to reviewSceneId.toString()))
					.entries.joinToString("&") { (k, v) ->
						"$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
					}
			)
		}

	@Test
	fun `reviewer creates a reword suggestion and it persists`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val response = postSuggestion(
			sceneId,
			mapOf(
				"type" to "reword",
				"paragraph" to "0",
				"start" to "25", // "non-viable"
				"end" to "35",
				"replacement" to "nonviable",
				"reason" to "House style",
			),
		)
		assertEquals(HttpStatusCode.OK, response.status)
		assertContains(response.bodyAsText(), "\"id\":")

		val stored = database().serverDatabase.reviewSuggestionQueries
			.getSuggestionsForScene(sceneId).executeAsOne()
		assertEquals("reword", stored.type)
		assertEquals("non-viable", stored.original_text)
		assertEquals("nonviable", stored.replacement_text)

		// Activity flipped to in-progress on first edit
		val status = database().serverDatabase.reviewRequestQueries
			.getRequestByToken(tokenHasher().hashToken(plainToken)).executeAsOne().status
		assertEquals(ReviewStatus.IN_PROGRESS.toStringId(), status)
	}

	@Test
	fun `overlapping suggestion is rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val first = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "25", "end" to "35"))
		assertEquals(HttpStatusCode.OK, first.status)

		val overlap = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "30", "end" to "40"))
		assertEquals(HttpStatusCode.Conflict, overlap.status)

		assertEquals(1, database().serverDatabase.reviewSuggestionQueries.countSuggestionsForRequest(
			database().serverDatabase.reviewSceneQueries.getScene(sceneId).executeAsOne().review_request_id
		).executeAsOne())
	}

	@Test
	fun `out-of-range offsets are rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val response = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "5", "end" to "999"))
		assertEquals(HttpStatusCode.Conflict, response.status)
	}

	@Test
	fun `reviewer deletes a suggestion`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val created = postSuggestion(sceneId, mapOf("type" to "comment", "paragraph" to "0", "start" to "25", "end" to "35"))
		val id = Regex("\"id\":(\\d+)").find(created.bodyAsText())!!.groupValues[1]

		val del = client().delete(route("review/$plainToken/suggestions/$id"))
		assertEquals(HttpStatusCode.NoContent, del.status)

		assertNull(
			database().serverDatabase.reviewSuggestionQueries.getSuggestionsForScene(sceneId).executeAsOneOrNull()
		)
	}

	@Test
	fun `submit locks the review against further edits`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val submit = client().post(route("review/$plainToken/submit"))
		assertEquals(HttpStatusCode.OK, submit.status)

		val status = database().serverDatabase.reviewRequestQueries
			.getRequestByToken(tokenHasher().hashToken(plainToken)).executeAsOne().status
		assertEquals(ReviewStatus.SUBMITTED.toStringId(), status)

		// further edits refused
		val after = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "0", "end" to "2"))
		assertEquals(HttpStatusCode.Conflict, after.status)
	}
}
