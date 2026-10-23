package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.project.GroupNameTaken
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectsRoutesDeleteGroupTest : ProjectsRoutesBaseTest() {

	private val syncId = "syncId-test"
	private val userId = 0L
	private val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")

	private fun ApplicationTestBuilder.authorize() {
		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		defaultApplication()
	}

	private suspend fun ApplicationTestBuilder.postDelete(
		query: String = "groupId=${groupId.id}",
		withSyncId: Boolean = true,
	): HttpResponse = client.post("api/projects/0/group/delete?$query") {
		header("Authorization", "Bearer $BEARER_TOKEN")
		if (withSyncId) header(HEADER_SYNC_ID, syncId)
	}

	@Test
	fun `Delete Group - Success`() = testApplication {
		coEvery { projectsRepository.deleteGroup(userId, syncId, groupId) } returns SResult.success()
		authorize()

		postDelete(query = "groupId=${groupId.id.uppercase()}").apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify { projectsRepository.deleteGroup(userId, syncId, groupId) }
		}
	}

	@Test
	fun `Delete Group - Missing group id`() = testApplication {
		authorize()

		postDelete(query = "").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.deleteGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Delete Group - Malformed group id`() = testApplication {
		authorize()

		postDelete(query = "groupId=not-a-uuid").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.deleteGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Delete Group - Missing sync id`() = testApplication {
		authorize()

		postDelete(withSyncId = false).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.deleteGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Delete Group - Bad sync id`() = testApplication {
		coEvery { projectsRepository.deleteGroup(userId, syncId, groupId) } returns
			SResult.failure(InvalidSyncIdException())
		authorize()

		postDelete().apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Delete Group - Child name collision`() = testApplication {
		coEvery { projectsRepository.deleteGroup(userId, syncId, groupId) } returns
			SResult.failure(GroupNameTaken("Drafts"))
		authorize()

		postDelete().apply {
			assertEquals(HttpStatusCode.Conflict, status)
			assertEquals(
				ApiErrorCode.GROUP_NAME_TAKEN,
				Json.decodeFromString<HttpResponseError>(bodyAsText()).errorCode,
			)
		}
	}
}
