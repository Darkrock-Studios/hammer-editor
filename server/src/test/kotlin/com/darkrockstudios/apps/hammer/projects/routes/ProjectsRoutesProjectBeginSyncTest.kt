package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsBeginSyncData
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRoutesProjectBeginSyncTest : ProjectsRoutesBaseTest() {

	@Test
	fun `Projects - Begin Sync - Success`() = testApplication {
		val syncId = "syncId-test"
		val userId = 0L

		val syncData = ProjectsBeginSyncData(
			syncId = syncId,
			projects = setOf(
				ProjectDefinition("Project 1", ProjectId("uuid-1")),
				ProjectDefinition("Project 2", ProjectId("uuid-2")),
			),
			deletedProjects = setOf(
				ProjectId("uuid-3"),
			),
		)

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.beginProjectsSync(userId = userId)
		} returns SResult.success(syncData)

		defaultApplication()

		client.get("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.OK, status)

			val responseBody = bodyAsText()
			Json.decodeFromString<BeginProjectsSyncResponse>(responseBody).apply {
				assertEquals(syncId, this.syncId)
				assertEquals(syncData.projects.map { it.toApi() }.toSet(), this.projects)
				syncData.deletedProjects.forEach { syncProject ->
					val found = deletedProjects.any { it == syncProject }
					assertTrue(found, "Deleted project not found: $syncProject")
				}
			}

			coVerify { projectsRepository.beginProjectsSync(userId = userId) }
		}
	}

	@Test
	fun `Projects - Begin Sync - Active Sync Session Conflict`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.beginProjectsSync(userId = any())
		} returns SResult.failure(Exception())

		defaultApplication()

		client.get("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Begin Sync - Bad Auth`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.failure(Exception())
		coEvery { whiteListRepository.useWhiteList() } returns false
		defaultApplication()

		client.get("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.Unauthorized, status)
		}
	}
}