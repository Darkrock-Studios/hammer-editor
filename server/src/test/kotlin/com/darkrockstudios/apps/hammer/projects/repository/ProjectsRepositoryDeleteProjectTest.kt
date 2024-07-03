package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryDeleteProjectTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Delete Project, conflicting projects sync session`() = runTest {
		val syncId = "sync-id"
		val projectName = "Project Name"

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectsRepository().apply {
			val result = deleteProject(userId, syncId, projectName)
			assertTrue(result.isFailure)
			verify(exactly = 0) { projectDatasource.deleteProject(any(), any()) }
			verify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
		}
	}

	@Test
	fun `Delete Project, successful`() = runTest {
		val syncId = "sync-id"
		val projectName = "Project Name"

		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		every { projectDatasource.deleteProject(userId, projectName) } returns Result.success(Unit)

		val initialSyncData = ProjectsSyncData(
			lastSync = Instant.DISTANT_PAST,
			deletedProjects = setOf(
				"Project 2"
			),
		)

		var updatedSyncData: ProjectsSyncData? = null
		every { projectsDatasource.updateSyncData(userId, captureLambda()) } answers {
			lambda<(ProjectsSyncData) -> ProjectsSyncData>().captured.invoke(initialSyncData)
				.apply {
					updatedSyncData = this
				}
		}

		val expectedSyncData = ProjectsSyncData(
			lastSync = Instant.DISTANT_PAST,
			deletedProjects = setOf(
				"Project 2",
				projectName,
			),
		)

		createProjectsRepository().apply {
			val result = deleteProject(userId, syncId, projectName)

			assertTrue(result.isSuccess)
			verify { projectDatasource.deleteProject(userId, projectName) }
			verify { projectsDatasource.updateSyncData(userId, any()) }

			assertEquals(expectedSyncData, updatedSyncData)
		}
	}
}