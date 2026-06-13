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
	fun `a range edit spanning an existing insert caret is rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val caret = postSuggestion(
			sceneId,
			mapOf("type" to "insert", "paragraph" to "0", "start" to "28", "end" to "28", "replacement" to "X"),
		)
		assertEquals(HttpStatusCode.OK, caret.status)

		// A delete spanning the caret would swallow the insertion at apply time
		val spanning = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "25", "end" to "35"))
		assertEquals(HttpStatusCode.Conflict, spanning.status)

		// Touching the caret only at its boundary is fine
		val adjacent = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "25", "end" to "28"))
		assertEquals(HttpStatusCode.OK, adjacent.status)
	}

	@Test
	fun `out-of-range offsets are rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val response = postSuggestion(sceneId, mapOf("type" to "delete", "paragraph" to "0", "start" to "5", "end" to "999"))
		assertEquals(HttpStatusCode.Conflict, response.status)
	}

	@Test
	fun `over-long suggestion content is rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val longReplacement = postSuggestion(
			sceneId,
			mapOf("type" to "reword", "paragraph" to "0", "start" to "25", "end" to "35", "replacement" to "x".repeat(10_001)),
		)
		assertEquals(HttpStatusCode.Conflict, longReplacement.status)

		val longComment = postSuggestion(
			sceneId,
			mapOf("type" to "comment", "paragraph" to "0", "start" to "25", "end" to "35", "reason" to "x".repeat(5_001)),
		)
		assertEquals(HttpStatusCode.Conflict, longComment.status)

		// At the cap is still fine
		val atCap = postSuggestion(
			sceneId,
			mapOf("type" to "reword", "paragraph" to "0", "start" to "25", "end" to "35", "replacement" to "x".repeat(10_000)),
		)
		assertEquals(HttpStatusCode.OK, atCap.status)
	}

	@Test
	fun `reviewer edits an existing suggestion`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val created = postSuggestion(
			sceneId,
			mapOf("type" to "reword", "paragraph" to "0", "start" to "25", "end" to "35", "replacement" to "nonviable"),
		)
		val id = Regex("\"id\":(\\d+)").find(created.bodyAsText())!!.groupValues[1]

		val update = client().post(route("review/$plainToken/suggestions/$id")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("replacement=${java.net.URLEncoder.encode("not viable", "UTF-8")}&reason=${java.net.URLEncoder.encode("Softer phrasing", "UTF-8")}")
		}
		assertEquals(HttpStatusCode.OK, update.status)

		val stored = database().serverDatabase.reviewSuggestionQueries
			.getSuggestionsForScene(sceneId).executeAsOne()
		assertEquals("not viable", stored.replacement_text)
		assertEquals("Softer phrasing", stored.reason)
		// anchors untouched by content edits
		assertEquals(25, stored.start_offset)
		assertEquals(35, stored.end_offset)
	}

	@Test
	fun `editing a reword to an empty replacement is rejected`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val created = postSuggestion(
			sceneId,
			mapOf("type" to "reword", "paragraph" to "0", "start" to "25", "end" to "35", "replacement" to "nonviable"),
		)
		val id = Regex("\"id\":(\\d+)").find(created.bodyAsText())!!.groupValues[1]

		val update = client().post(route("review/$plainToken/suggestions/$id")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("replacement=&reason=x")
		}
		assertEquals(HttpStatusCode.Conflict, update.status)
	}

	@Test
	fun `reviewer deletes a suggestion`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()
		val created = postSuggestion(
			sceneId,
			mapOf("type" to "comment", "paragraph" to "0", "start" to "25", "end" to "35", "reason" to "Needs a citation"),
		)
		val id = Regex("\"id\":(\\d+)").find(created.bodyAsText())!!.groupValues[1]

		val del = client().delete(route("review/$plainToken/suggestions/$id"))
		assertEquals(HttpStatusCode.NoContent, del.status)

		assertNull(
			database().serverDatabase.reviewSuggestionQueries.getSuggestionsForScene(sceneId).executeAsOneOrNull()
		)
	}

	@Test
	fun `editor marks a scene done and can unmark it`(): Unit = runBlocking {
		doStartServer()
		val sceneId = seed()

		val mark = client().post(route("review/$plainToken/scenes/$sceneId/done")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("done=true")
		}
		assertEquals(HttpStatusCode.OK, mark.status)
		assertEquals(
			true,
			database().serverDatabase.reviewSceneQueries.getScene(sceneId).executeAsOne().reviewer_done,
		)

		// Counts as activity
		val status = database().serverDatabase.reviewRequestQueries
			.getRequestByToken(tokenHasher().hashToken(plainToken)).executeAsOne().status
		assertEquals(ReviewStatus.IN_PROGRESS.toStringId(), status)

		val unmark = client().post(route("review/$plainToken/scenes/$sceneId/done")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("done=false")
		}
		assertEquals(HttpStatusCode.OK, unmark.status)
		assertEquals(
			false,
			database().serverDatabase.reviewSceneQueries.getScene(sceneId).executeAsOne().reviewer_done,
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
