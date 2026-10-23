package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.project.InvalidProjectName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectDeletedException
import com.darkrockstudios.apps.hammer.project.ProjectIdTakenException
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository.ProjectCreatedResult
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRoutesCreateProjectTest : ProjectsRoutesBaseTest() {

	private val projectName = "TestProject"
	private val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
	private val syncId = "syncId-test"
	private val userId = 0L

	private fun ApplicationTestBuilder.authorize() {
		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		defaultApplication()
	}

	private suspend fun ApplicationTestBuilder.postCreate(
		query: String = "projectName=$projectName&projectId=${projectId.id}",
		withSyncId: Boolean = true,
	): HttpResponse = client.post("api/projects/0/create?$query") {
		header("Authorization", "Bearer $BEARER_TOKEN")
		if (withSyncId) header(HEADER_SYNC_ID, syncId)
	}

	private fun stubResult(result: SResult<ProjectCreatedResult>) {
		coEvery {
			projectsRepository.createProject(userId, syncId, projectName, projectId)
		} returns result
	}

	@Test
	fun `Projects - Create Project - Success`() = testApplication {
		stubResult(
			SResult.success(ProjectCreatedResult(ProjectDefinition(projectName, projectId), alreadyExisted = false))
		)
		authorize()

		postCreate().apply {
			assertTrue(status.isSuccess())
			val response = Json.decodeFromString<CreateProjectResponse>(bodyAsText())
			assertEquals(projectId, response.projectId)
			assertFalse(response.alreadyExisted)
			coVerify { projectsRepository.createProject(userId, syncId, projectName, projectId) }
		}
	}

	@Test
	fun `Projects - Create Project - Group is forwarded and placement returned`() = testApplication {
		val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")
		coEvery {
			projectsRepository.createProject(userId, syncId, projectName, projectId, groupId)
		} returns SResult.success(
			ProjectCreatedResult(ProjectDefinition(projectName, projectId), alreadyExisted = false, groupId = groupId)
		)
		authorize()

		postCreate(query = "projectName=$projectName&projectId=${projectId.id}&groupId=${groupId.id.uppercase()}").apply {
			assertTrue(status.isSuccess())
			val response = Json.decodeFromString<CreateProjectResponse>(bodyAsText())
			assertEquals(groupId, response.groupId)
			coVerify { projectsRepository.createProject(userId, syncId, projectName, projectId, groupId) }
		}
	}

	@Test
	fun `Projects - Create Project - Malformed groupId`() = testApplication {
		authorize()

		postCreate(query = "projectName=$projectName&projectId=${projectId.id}&groupId=not-a-uuid").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.createProject(any(), any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Projects - Create Project - Upper case id is canonicalized`() = testApplication {
		stubResult(
			SResult.success(ProjectCreatedResult(ProjectDefinition(projectName, projectId), alreadyExisted = false))
		)
		authorize()

		postCreate(query = "projectName=$projectName&projectId=${projectId.id.uppercase()}").apply {
			assertTrue(status.isSuccess())
			coVerify { projectsRepository.createProject(userId, syncId, projectName, projectId) }
		}
	}

	@Test
	fun `Projects - Create Project - GET is not routed`() = testApplication {
		authorize()

		client.get("api/projects/0/create?projectName=$projectName&projectId=${projectId.id}") {
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)
		}.apply {
			assertFalse(status.isSuccess())
			coVerify(exactly = 0) { projectsRepository.createProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Projects - Create Project - Missing projectId`() = testApplication {
		authorize()

		postCreate(query = "projectName=$projectName").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.createProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Projects - Create Project - Malformed projectId`() = testApplication {
		authorize()

		postCreate(query = "projectName=$projectName&projectId=not-a-uuid").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.createProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Projects - Create Project - Missing sync id`() = testApplication {
		authorize()

		postCreate(withSyncId = false).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.createProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Bad SyncId`() = testApplication {
		stubResult(SResult.failure(InvalidSyncIdException()))
		authorize()

		postCreate().apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Invalid name`() = testApplication {
		stubResult(SResult.failure(InvalidProjectName(projectName)))
		authorize()

		postCreate().apply {
			assertEquals(HttpStatusCode.NotAcceptable, status)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Deleted`() = testApplication {
		stubResult(SResult.failure(ProjectDeletedException(projectId)))
		authorize()

		postCreate().apply {
			assertEquals(HttpStatusCode.Conflict, status)
			val error = Json.decodeFromString<HttpResponseError>(bodyAsText())
			assertEquals(ApiErrorCode.PROJECT_DELETED, error.errorCode)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Id taken`() = testApplication {
		stubResult(SResult.failure(ProjectIdTakenException(projectId)))
		authorize()

		postCreate().apply {
			assertEquals(HttpStatusCode.Conflict, status)
			val error = Json.decodeFromString<HttpResponseError>(bodyAsText())
			assertEquals(ApiErrorCode.PROJECT_ID_TAKEN, error.errorCode)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Repository Exception`() = testApplication {
		stubResult(SResult.failure(Exception()))
		authorize()

		postCreate().apply {
			assertEquals(HttpStatusCode.InternalServerError, status)
		}
	}
}
