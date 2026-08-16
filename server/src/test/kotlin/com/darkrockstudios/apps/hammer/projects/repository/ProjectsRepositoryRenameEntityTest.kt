package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.validate.MAX_PROJECT_NAME_LENGTH
import com.darkrockstudios.apps.hammer.project.ProjectNameTaken
import com.darkrockstudios.apps.hammer.utilities.isFailure
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectsRepositoryRenameEntityTest : ProjectsRepositoryBaseTest() {
	@Test
	fun `Rename Project - Success`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")
		val newProjectName = "New Project Name"

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectEntityDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectEntityDatasource.renameProject(any(), any(), any()) } returns true

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(result.isSuccess)
			coVerify { projectEntityDatasource.renameProject(userId, projectId, newProjectName) }
		}
	}

	@Test
	fun `Rename Project - Failure - Name Already Taken`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")
		val newProjectName = "Taken Project Name"

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectEntityDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectEntityDatasource.renameProject(any(), any(), any()) } returns false

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(isFailure(result))
			assertIs<ProjectNameTaken>(result.exception)
		}
	}

	companion object {
		@JvmStatic
		fun provideBadNames(): Stream<String> = Stream.of(
			"1".repeat(MAX_PROJECT_NAME_LENGTH + 1),
			"",
		)
	}

	@ParameterizedTest
	@MethodSource("provideBadNames")
	fun `Rename Project - Failure - Bad Name`(newProjectName: String) = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectEntityDatasource.checkProjectExists(any(), projectId) } returns true
		coEvery { projectEntityDatasource.renameProject(any(), any(), any()) } returns true

		createProjectsRepository().apply {
			val result = renameProject(userId, syncId, projectId, newProjectName)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectEntityDatasource.renameProject(any(), any(), any()) }
		}
	}
}
