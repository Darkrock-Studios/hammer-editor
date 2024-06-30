package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRoutesCreateProjectTest : ProjectsRoutesBaseTest() {

	@Test
	fun `Projects - Create Project - Success`() = testApplication {
		val projectName = "TestProject"
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.createProject(
				userId = userId,
				syncId = syncId,
				projectName = projectName,
			)
		} returns Result.success(Unit)

		defaultApplication()

		client.get("api/projects/0/TestProject/create") {
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)
		}.apply {
			assertTrue(status.isSuccess())
			coVerify {
				projectsRepository.createProject(
					userId = userId,
					syncId = syncId,
					projectName = projectName,
				)
			}
		}
	}

	@Test
	fun `Projects - Create Project - Invalid Request`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false

		defaultApplication()

		client.get("api/projects/0/TestProject/create") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Bad SyncId`() = testApplication {
		val projectName = "TestProject"
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.createProject(
				userId = userId,
				syncId = syncId,
				projectName = projectName,
			)
		} returns Result.failure(InvalidSyncIdException())

		defaultApplication()

		client.get("api/projects/0/TestProject/create") {
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Create Project - Failure - Repository Exception`() = testApplication {
		val projectName = "TestProject"
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.createProject(
				userId = userId,
				syncId = syncId,
				projectName = projectName,
			)
		} returns Result.failure(Exception())

		defaultApplication()

		client.get("api/projects/0/TestProject/create") {
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)
		}.apply {
			assertEquals(HttpStatusCode.InternalServerError, status)
		}
	}
}

