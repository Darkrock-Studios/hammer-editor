package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.InvalidProjectName
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectDeletedException
import com.darkrockstudios.apps.hammer.project.ProjectIdTakenException
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectsRepositoryCreateProjectTest : ProjectsRepositoryBaseTest() {

	private val syncId = "sync-id"
	private val projectName = "Project Name"
	private val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")

	@BeforeEach
	fun setupDefaults() {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns null
		coEvery { projectsDatasource.loadSyncData(userId) } returns ProjectsSyncData(deletedProjects = emptySet())
		coEvery { projectEntityDatasource.findProjectByName(userId, projectName) } returns null
		coEvery {
			projectEntityDatasource.createProject(userId, projectName, projectId)
		} returns ProjectDefinition(projectName, projectId)
	}

	@Test
	fun `Create Project, conflicting projects sync session`() = runTest {
		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectEntityDatasource.createProject(any(), any(), any()) }
			coVerify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
		}
	}

	@Test
	fun `Create Project, invalid name`() = runTest {
		createProjectsRepository().apply {
			val result = createProject(userId, syncId, "", projectId)
			assertIs<InvalidProjectName>((result as ServerResult.Failure).exception)
			coVerify(exactly = 0) { projectEntityDatasource.createProject(any(), any(), any()) }
		}
	}

	@Test
	fun `Create Project, successful with the client id`() = runTest {
		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertTrue(isSuccess(result))
			assertEquals(ProjectDefinition(projectName, projectId), result.data.project)
			assertFalse(result.data.alreadyExisted)
			coVerify { projectEntityDatasource.createProject(userId, projectName, projectId) }
		}
	}

	@Test
	fun `Create Project, existing id is idempotent`() = runTest {
		val existing = ProjectDefinition("Older Name", projectId)
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns existing

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertTrue(isSuccess(result))
			assertEquals(existing, result.data.project)
			assertTrue(result.data.alreadyExisted)
			coVerify(exactly = 0) { projectEntityDatasource.createProject(any(), any(), any()) }
		}
	}

	@Test
	fun `Create Project, tombstoned id is refused`() = runTest {
		coEvery { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(deletedProjects = setOf(projectId))

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertIs<ProjectDeletedException>((result as ServerResult.Failure).exception)
			coVerify(exactly = 0) { projectEntityDatasource.createProject(any(), any(), any()) }
		}
	}

	@Test
	fun `Create Project, same name under another id returns the existing id`() = runTest {
		val existing = ProjectDefinition(projectName, ProjectId("9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d"))
		coEvery { projectEntityDatasource.findProjectByName(userId, projectName) } returns existing

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertTrue(isSuccess(result))
			assertEquals(existing, result.data.project)
			assertTrue(result.data.alreadyExisted)
			coVerify(exactly = 0) { projectEntityDatasource.createProject(any(), any(), any()) }
		}
	}

	@Test
	fun `Create Project, id held by another account is refused`() = runTest {
		coEvery { projectEntityDatasource.createProject(userId, projectName, projectId) } returns null

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertIs<ProjectIdTakenException>((result as ServerResult.Failure).exception)
		}
	}

	@Test
	fun `Create Project, losing the insert to a concurrent retry of the same create is idempotent`() = runTest {
		val created = ProjectDefinition(projectName, projectId)
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returnsMany listOf(null, created)
		coEvery { projectEntityDatasource.createProject(userId, projectName, projectId) } returns null

		createProjectsRepository().apply {
			val result = createProject(userId, syncId, projectName, projectId)

			assertTrue(isSuccess(result))
			assertEquals(created, result.data.project)
			assertTrue(result.data.alreadyExisted)
		}
	}
}
