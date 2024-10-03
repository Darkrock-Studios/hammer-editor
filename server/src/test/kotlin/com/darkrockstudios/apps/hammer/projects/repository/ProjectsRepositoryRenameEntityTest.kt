package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.validate.MAX_PROJECT_NAME_LENGTH
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

class ProjectsRepositoryRenameEntityTest : ProjectsRepositoryBaseTest() {
	@Test
	fun `Rename Project - Success`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")
		val newProjectName = "New Project Name"

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectDatasource.renameProject(any(), any(), any()) } returns true

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(result.isSuccess)
			coVerify(exactly = 1) { projectDatasource.renameProject(any(), any(), any()) }
		}
	}

	@Test
	fun `Rename Project - Failure - Bad Name`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")
		val newProjectName = "1".repeat(MAX_PROJECT_NAME_LENGTH + 1)

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectDatasource.renameProject(any(), any(), any()) } returns true

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectDatasource.renameProject(any(), any(), any()) }
		}
	}

	@ParameterizedTest
	@ValueSource(
		strings = [
			"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
			""
		]
	)
	fun `Rename Project - Failure - Bad Name`(newProjectName: String) = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectDatasource.renameProject(any(), any(), any()) } returns true

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectDatasource.renameProject(any(), any(), any()) }
		}
	}
}