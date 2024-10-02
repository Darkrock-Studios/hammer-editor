package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectsRoutesDeleteProjectTest : ProjectsRoutesBaseTest() {

	@Test
	fun `Projects - Delete Project - Success`() = testApplication {
		val projectId = ProjectId("TestProjectId")
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.deleteProject(
				userId = userId,
				syncId = syncId,
				projectId = projectId,
			)
		} returns SResult.success(Unit)

		defaultApplication()

		client.get("api/projects/0/delete") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)

			parameter("projectId", projectId.id)
		}.apply {
			assertEquals(HttpStatusCode.OK, status)
			coVerify {
				projectsRepository.deleteProject(
					userId = userId,
					syncId = syncId,
					projectId = projectId,
				)
			}
		}
	}

	@Test
	fun `Projects - Delete Project - Invalid Request`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false

		defaultApplication()

		client.get("api/projects/0/delete") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header("Authorization", "Bearer $BEARER_TOKEN")

			parameter("projectId", "project-id-1")
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Delete Project - Failure - Repository Exception`() = testApplication {
		val projectId = ProjectId("TestProjectId")
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.deleteProject(
				userId = userId,
				syncId = syncId,
				projectId = projectId,
			)
		} returns SResult.failure(Exception())

		defaultApplication()

		client.get("api/projects/0/delete") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)

			parameter("projectId", projectId)
		}.apply {
			assertEquals(HttpStatusCode.InternalServerError, status)
		}
	}
}