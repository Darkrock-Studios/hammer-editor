package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.Test
import java.util.stream.Stream
import kotlin.test.assertEquals

class ProjectsRoutesMoveProjectTest : ProjectsRoutesBaseTest() {

	private val syncId = "syncId-test"
	private val userId = 0L
	private val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
	private val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")

	private fun ApplicationTestBuilder.authorize() {
		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		defaultApplication()
	}

	private suspend fun ApplicationTestBuilder.postMove(
		query: String = "projectId=${projectId.id}&groupId=${groupId.id}",
		withSyncId: Boolean = true,
	): HttpResponse = client.post("api/projects/0/move?$query") {
		header("Authorization", "Bearer $BEARER_TOKEN")
		if (withSyncId) header(HEADER_SYNC_ID, syncId)
	}

	@Test
	fun `Move Project - Into a group`() = testApplication {
		coEvery { projectsRepository.moveProject(userId, syncId, projectId, groupId) } returns SResult.success()
		authorize()

		postMove(query = "projectId=${projectId.id}&groupId=${groupId.id.uppercase()}").apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify { projectsRepository.moveProject(userId, syncId, projectId, groupId) }
		}
	}

	@Test
	fun `Move Project - No group means the top level`() = testApplication {
		coEvery { projectsRepository.moveProject(userId, syncId, projectId, null) } returns SResult.success()
		authorize()

		postMove(query = "projectId=${projectId.id}").apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify { projectsRepository.moveProject(userId, syncId, projectId, null) }
		}
	}

	@Test
	fun `Move Project - Malformed group id`() = testApplication {
		authorize()

		postMove(query = "projectId=${projectId.id}&groupId=not-a-uuid").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.moveProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Move Project - Missing project id`() = testApplication {
		authorize()

		postMove(query = "groupId=${groupId.id}").apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.moveProject(any(), any(), any(), any()) }
		}
	}

	@Test
	fun `Move Project - Missing sync id`() = testApplication {
		authorize()

		postMove(withSyncId = false).apply {
			assertEquals(HttpStatusCode.BadRequest, status)
			coVerify(exactly = 0) { projectsRepository.moveProject(any(), any(), any(), any()) }
		}
	}

	@ParameterizedTest
	@MethodSource("failures")
	fun `Move Project - Repository failure maps to status`(
		failure: Exception,
		expectedStatus: HttpStatusCode,
	) = testApplication {
		coEvery { projectsRepository.moveProject(userId, syncId, projectId, groupId) } returns SResult.failure(failure)
		authorize()

		postMove().apply {
			assertEquals(expectedStatus, status)
		}
	}

	companion object {
		@JvmStatic
		fun failures(): Stream<Arguments> = Stream.of(
			Arguments.of(InvalidSyncIdException(), HttpStatusCode.BadRequest),
			Arguments.of(ProjectNotFound(ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")), HttpStatusCode.NotFound),
			Arguments.of(Exception(), HttpStatusCode.InternalServerError),
		)
	}
}
