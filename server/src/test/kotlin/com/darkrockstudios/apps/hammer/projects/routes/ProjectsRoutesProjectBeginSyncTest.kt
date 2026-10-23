package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectGroup
import com.darkrockstudios.apps.hammer.projects.ProjectsBeginSyncData
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
		coEvery { accountsRepository.getInstallId(BEARER_TOKEN) } returns "test-install-id"
		coEvery {
			projectsRepository.beginProjectsSync(userId, "test-install-id")
		} returns SResult.success(syncData)

		defaultApplication()

		client.post("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.OK, status)

			val responseBody = bodyAsText()
			Json.decodeFromString<BeginProjectsSyncResponse>(responseBody).apply {
				assertEquals(syncId, this.syncId)
				assertEquals(syncData.projects.map { it.toApi() }.toSet(), this.projects)
				assertEquals(syncData.deletedProjects, this.deletedProjects)
			}

			coVerify { projectsRepository.beginProjectsSync(userId, "test-install-id") }
		}
	}

	@Test
	fun `Projects - Begin Sync - Carries groups and project placement`() = testApplication {
		val userId = 0L
		val series = GroupId("0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b")
		val trilogy = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")
		val syncData = ProjectsBeginSyncData(
			syncId = "syncId-test",
			projects = setOf(
				ProjectDefinition("Grouped", ProjectId("uuid-1")),
				ProjectDefinition("Loose", ProjectId("uuid-2")),
			),
			deletedProjects = emptySet(),
			groups = setOf(
				ProjectGroup(1L, series, "Series", null, null, null),
				ProjectGroup(2L, trilogy, "Trilogy", "three books", 1L, series),
			),
			projectPlacements = mapOf(ProjectId("uuid-1") to trilogy),
		)

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { projectsRepository.beginProjectsSync(userId, any()) } returns SResult.success(syncData)

		defaultApplication()

		client.post("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.OK, status)

			Json.decodeFromString<BeginProjectsSyncResponse>(bodyAsText()).apply {
				assertEquals(
					setOf(
						ApiProjectDefinition("Grouped", ProjectId("uuid-1"), trilogy),
						ApiProjectDefinition("Loose", ProjectId("uuid-2"), null),
					),
					projects,
				)
				assertEquals(
					setOf(
						ApiGroupDefinition(series, "Series", null, null),
						ApiGroupDefinition(trilogy, "Trilogy", series, "three books"),
					),
					groups,
				)
			}
		}
	}

	@Test
	fun `Projects - Begin Sync - Active Sync Session Conflict`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.success(0L)
		coEvery {
			projectsRepository.beginProjectsSync(any(), any())
		} returns SResult.failure(Exception())

		defaultApplication()

		client.post("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)
		}
	}

	@Test
	fun `Projects - Begin Sync - Bad Auth`() = testApplication {
		coEvery { accountsRepository.checkToken(any(), any()) } returns SResult.failure(Exception())
		defaultApplication()

		client.post("api/projects/0/begin_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.Unauthorized, status)
		}
	}
}