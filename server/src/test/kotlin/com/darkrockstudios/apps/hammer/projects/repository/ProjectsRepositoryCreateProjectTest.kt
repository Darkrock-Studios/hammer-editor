package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ProjectsRepositoryCreateProjectTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Create Project, conflicting projects sync session`() = runTest {
		val syncId = "sync-id"
		val projectName = "Project Name"

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectDatasource.createProject(any(), any()) }
			coVerify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
		}
	}

	@Test
	fun `Create Project, successful`() = runTest {
		val syncId = "sync-id"
		val projectName = "Project Name"
		val projectId = ProjectId("uuid-1")

		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectDatasource.findProjectByName(userId, projectName) } returns null
		coEvery {
			projectDatasource.createProject(
				userId,
				projectName
			)
		} returns ProjectDefinition(projectName, projectId)

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName)

			assertTrue(result.isSuccess)
			coVerify { projectDatasource.createProject(userId, projectName) }
		}
	}
}