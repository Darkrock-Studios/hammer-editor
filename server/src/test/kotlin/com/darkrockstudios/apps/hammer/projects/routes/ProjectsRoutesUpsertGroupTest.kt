package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.UpsertGroupResponse
import com.darkrockstudios.apps.hammer.project.GroupCycleException
import com.darkrockstudios.apps.hammer.project.GroupIdTakenException
import com.darkrockstudios.apps.hammer.project.GroupNameTaken
import com.darkrockstudios.apps.hammer.project.GroupTooDeepException
import com.darkrockstudios.apps.hammer.project.InvalidGroupDescription
import com.darkrockstudios.apps.hammer.project.InvalidGroupName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository.GroupUpsertResult
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ProjectsRoutesUpsertGroupTest : ProjectsRoutesBaseTest() {

	private val syncId = "syncId-test"
	private val userId = 0L
	private val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")
	private val parentId = GroupId("0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b")
	private val group = ApiGroupDefinition(groupId, "Trilogy", parentId, "three books")

	private fun ApplicationTestBuilder.authorize() {
		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		defaultApplication()
	}

	private suspend fun ApplicationTestBuilder.postUpsert(
		body: String = Json.encodeToString(group),
		withSyncId: Boolean = true,
	): HttpResponse = client.post("api/projects/0/group/upsert") {
		header("Authorization", "Bearer $BEARER_TOKEN")
		if (withSyncId) header(HEADER_SYNC_ID, syncId)
		contentType(ContentType.Application.Json)
		setBody(body)
	}

	@Test
	fun `Upsert Group - Success`() = testApplication {
		coEvery { projectsRepository.upsertGroup(userId, syncId, group) } returns
			SResult.success(GroupUpsertResult(groupId, alreadyExisted = false))
		authorize()

		postUpsert().apply {
			assertEquals(HttpStatusCode.OK, status)
			val response = Json.decodeFromString<UpsertGroupResponse>(bodyAsText())
			assertEquals(UpsertGroupResponse(groupId, alreadyExisted = false), response)
		}
	}

	@Test
	fun `Upsert Group - Upper case ids are canonicalized`() = testApplication {
		coEvery { projectsRepository.upsertGroup(userId, syncId, group) } returns
			SResult.success(GroupUpsertResult(groupId, alreadyExisted = true))
		authorize()

		val shouting = group.copy(uuid = GroupId(groupId.id.uppercase()), parentId = GroupId(parentId.id.uppercase()))
		postUpsert(body = Json.encodeToString(shouting)).apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify { projectsRepository.upsertGroup(userId, syncId, group) }
		}
	}

	@Test
	fun `Upsert Group - Malformed body`() = testApplication {
		authorize()

		postUpsert(body = "{ not json").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.upsertGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Upsert Group - Malformed group id`() = testApplication {
		authorize()

		postUpsert(body = Json.encodeToString(group.copy(uuid = GroupId("not-a-uuid")))).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.upsertGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Upsert Group - Malformed parent id`() = testApplication {
		authorize()

		postUpsert(body = Json.encodeToString(group.copy(parentId = GroupId("not-a-uuid")))).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.upsertGroup(any(), any(), any()) }
		}
	}

	@Test
	fun `Upsert Group - Missing sync id`() = testApplication {
		authorize()

		postUpsert(withSyncId = false).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.upsertGroup(any(), any(), any()) }
		}
	}

	@ParameterizedTest
	@MethodSource("failures")
	fun `Upsert Group - Repository failure maps to status and code`(
		failure: Exception,
		expectedStatus: HttpStatusCode,
		expectedCode: String?,
	) = testApplication {
		coEvery { projectsRepository.upsertGroup(userId, syncId, group) } returns SResult.failure(failure)
		authorize()

		postUpsert().apply {
			assertEquals(expectedStatus, status)
			assertEquals(expectedCode, Json.decodeFromString<HttpResponseError>(bodyAsText()).errorCode)
		}
	}

	companion object {
		private val id = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")

		@JvmStatic
		fun failures(): Stream<Arguments> = Stream.of(
			Arguments.of(InvalidSyncIdException(), HttpStatusCode.BadRequest, null),
			Arguments.of(InvalidGroupName("bad~name"), HttpStatusCode.NotAcceptable, null),
			Arguments.of(InvalidGroupDescription(5000), HttpStatusCode.NotAcceptable, null),
			Arguments.of(GroupNameTaken("Trilogy"), HttpStatusCode.Conflict, ApiErrorCode.GROUP_NAME_TAKEN),
			Arguments.of(GroupCycleException(id), HttpStatusCode.Conflict, ApiErrorCode.GROUP_CYCLE),
			Arguments.of(GroupTooDeepException(id), HttpStatusCode.Conflict, ApiErrorCode.GROUP_TOO_DEEP),
			Arguments.of(GroupIdTakenException(id), HttpStatusCode.Conflict, ApiErrorCode.GROUP_ID_TAKEN),
			Arguments.of(Exception(), HttpStatusCode.InternalServerError, null),
		)
	}
}
