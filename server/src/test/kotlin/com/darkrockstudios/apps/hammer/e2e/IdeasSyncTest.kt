package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaConflictDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaUploadRequest
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeasSyncStateResponse
import com.darkrockstudios.apps.hammer.base.http.storyideas.SavedIdeaDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class IdeasSyncTest : EndToEndTest() {

	private val userId = 1L
	private val ideaId = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c")

	private fun idea(content: String) = StoryIdea(
		id = ideaId,
		created = Instant.parse("2026-07-04T12:00:00Z"),
		updated = Instant.parse("2026-07-04T12:30:00Z"),
		title = "The Lighthouse Keeper's Daughter",
		content = content,
		tags = setOf("gothic", "coastal"),
	)

	private fun HttpRequestBuilder.authHeaders(token: String, syncId: String? = null) {
		headers {
			append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			append("Authorization", "Bearer $token")
			if (syncId != null) append(HEADER_SYNC_ID, syncId)
		}
	}

	private suspend fun HttpClient.fetchState(token: String, syncId: String): IdeasSyncStateResponse {
		val response = post(api("ideas/$userId/state")) { authHeaders(token, syncId) }
		assertEquals(HttpStatusCode.OK, response.status)
		return response.body()
	}

	@Test
	fun `Ideas endpoints require the account sync session`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// No syncId header at all
			val missing = post(api("ideas/$userId/state")) { authHeaders(authToken.auth) }
			assertEquals(HttpStatusCode.BadRequest, missing.status)

			// A syncId from thin air (no session open)
			val bogus = post(api("ideas/$userId/state")) { authHeaders(authToken.auth, "bogus-sync-id") }
			assertEquals(HttpStatusCode.BadRequest, bogus.status)
		}
	}

	@Test
	fun `Ideas Sync - Golden Path`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// The ideas phase rides the projects sync session
			val beginSyncResponse = get(api("projects/$userId/begin_sync")) {
				authHeaders(authToken.auth)
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
			val syncId = beginSyncResponse.body<BeginProjectsSyncResponse>().syncId

			// Fresh account: no ideas, no tombstones
			fetchState(authToken.auth, syncId).let { state ->
				assertTrue(state.ideas.isEmpty())
				assertTrue(state.deletedIdeas.isEmpty())
			}

			// Upload (baseline-less first upload)
			val original = idea("What if the light itself was the inheritance...")
			val originalHash = IdeaHasher.hash(original)
			val uploadResponse = post(api("ideas/$userId/idea/${ideaId.id}")) {
				authHeaders(authToken.auth, syncId)
				contentType(ContentType.Application.Json)
				setBody(IdeaUploadRequest(idea = original, hash = originalHash))
			}
			assertEquals(HttpStatusCode.OK, uploadResponse.status)
			assertEquals(originalHash, uploadResponse.body<SavedIdeaDto>().hash)

			// State now reports it
			fetchState(authToken.auth, syncId).let { state ->
				assertEquals(listOf(ideaId), state.ideas.map { it.id })
				assertEquals(listOf(originalHash), state.ideas.map { it.hash })
			}

			// Download round-trips the exact payload
			val downloadResponse = get(api("ideas/$userId/idea/${ideaId.id}")) {
				authHeaders(authToken.auth, syncId)
			}
			assertEquals(HttpStatusCode.OK, downloadResponse.status)
			downloadResponse.body<SavedIdeaDto>().let { dto ->
				assertEquals(original, dto.idea)
				assertEquals(originalHash, dto.hash)
			}

			// A stale baseline conflicts and returns the server copy
			val edited = idea("A completely different inheritance")
			val conflictResponse = post(api("ideas/$userId/idea/${ideaId.id}")) {
				authHeaders(authToken.auth, syncId)
				contentType(ContentType.Application.Json)
				setBody(
					IdeaUploadRequest(
						idea = edited,
						hash = IdeaHasher.hash(edited),
						originalHash = "stale-baseline",
					)
				)
			}
			assertEquals(HttpStatusCode.Conflict, conflictResponse.status)
			conflictResponse.body<IdeaConflictDto>().let { conflict ->
				assertEquals(original, conflict.server)
				assertEquals(originalHash, conflict.serverHash)
			}

			// The matching baseline overwrites
			val resolveResponse = post(api("ideas/$userId/idea/${ideaId.id}")) {
				authHeaders(authToken.auth, syncId)
				contentType(ContentType.Application.Json)
				setBody(
					IdeaUploadRequest(
						idea = edited,
						hash = IdeaHasher.hash(edited),
						originalHash = originalHash,
					)
				)
			}
			assertEquals(HttpStatusCode.OK, resolveResponse.status)

			// Delete writes a permanent tombstone
			val deleteResponse = post(api("ideas/$userId/idea/${ideaId.id}/delete")) {
				authHeaders(authToken.auth, syncId)
			}
			assertEquals(HttpStatusCode.OK, deleteResponse.status)

			fetchState(authToken.auth, syncId).let { state ->
				assertTrue(state.ideas.isEmpty())
				assertEquals(setOf(ideaId), state.deletedIdeas)
			}

			// Deletion wins: the tombstoned idea can never come back
			val resurrectResponse = post(api("ideas/$userId/idea/${ideaId.id}")) {
				authHeaders(authToken.auth, syncId)
				contentType(ContentType.Application.Json)
				setBody(IdeaUploadRequest(idea = edited, hash = IdeaHasher.hash(edited)))
			}
			assertEquals(HttpStatusCode.Gone, resurrectResponse.status)

			// End sync
			val endSyncResponse = post(api("projects/$userId/end_sync")) {
				authHeaders(authToken.auth, syncId)
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)
		}
	}
}
