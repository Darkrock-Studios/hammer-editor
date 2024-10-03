package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.project.InvalidProjectName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ProjectsRoutesRenameProjectTest : ProjectsRoutesBaseTest() {
	@Test
	fun `Rename a project successfully`() = testApplication {
		val projectId = ProjectId("TestProjectId")
		val syncId = "syncId-test"
		val userId = 0L
		val newName = "New Project Name"

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.renameProject(
				userId = userId,
				syncId = syncId,
				projectId = projectId,
				newProjectName = newName
			)
		} returns SResult.success(Unit)

		defaultApplication()

		client.get("api/projects/0/rename") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)

			parameter("projectId", projectId.id)
			parameter("projectName", newName)
		}.apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify {
				projectsRepository.renameProject(
					userId = userId,
					syncId = syncId,
					projectId = projectId,
					newProjectName = newName
				)
			}
		}
	}

	companion object {
		@JvmStatic
		fun provideFailureTestData(): Stream<Arguments> {
			return Stream.of(
				Arguments.of(
					SResult.failure<Unit>(InvalidProjectName("")),
					HttpStatusCode.NotAcceptable
				),
				Arguments.of(
					SResult.failure<Unit>(ProjectNotFound(ProjectId("TestProjectId"))),
					HttpStatusCode.NotFound
				),
				Arguments.of(
					SResult.failure<Unit>(
						InvalidSyncIdException()
					),
					HttpStatusCode.BadRequest
				),
			)
		}
	}

	@ParameterizedTest
	@MethodSource("provideFailureTestData")
	fun `Repository returned failure, ensure correct http status code returned`(
		failure: ServerResult<Unit>,
		expectedStatus: HttpStatusCode
	) = testApplication {
		val projectId = ProjectId("TestProjectId")
		val syncId = "syncId-test"
		val userId = 0L
		val newName = "What Ever"

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.renameProject(
				userId = userId,
				syncId = syncId,
				projectId = projectId,
				newProjectName = newName
			)
		} returns failure

		defaultApplication()

		client.get("api/projects/0/rename") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)

			parameter("projectId", projectId.id)
			parameter("projectName", newName)
		}.apply {
			assertEquals(expectedStatus, status)
		}
	}
}