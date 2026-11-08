package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.NotMemberException
import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectsRepositoryDeleteProjectTest : ProjectsRepositoryBaseTest() {

	private fun sharedRow(projectId: ProjectId) = Project(
		id = 40L,
		uuid = projectId.id,
		user_id = userId + 1,
		name = "Shared",
		last_id = 0,
		last_sync = Instant.DISTANT_PAST,
		group_id = null,
	)

	@Test
	fun `Delete Project, conflicting projects sync session`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("ProjectId")

		coEvery { projectsSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectsRepository().apply {
			val result = deleteProject(userId, syncId, projectId)
			assertTrue(result.isFailure)
			coVerify(exactly = 0) { projectEntityDatasource.deleteProject(any(), any()) }
			coVerify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
		}
	}

	@Test
	fun `Delete Project, successful`() = runTest {
		val syncId = "sync-id"
		val projectName = "Project Name"
		val projectId = ProjectId("project-id")
		val projectDef = ProjectDefinition(projectName, projectId)

		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery {
			projectEntityDatasource.deleteProject(
				OwnerId.unchecked(userId),
				projectId
			)
		} returns SResult.success(
			Unit
		)

		val initialSyncData = ProjectsSyncData(
			lastSync = Instant.DISTANT_PAST,
			deletedProjects = setOf(ProjectId("project-id-2")),
		)

		coEvery { projectsDatasource.getProject(userId, projectId) } returns projectDef

		var updatedSyncData: ProjectsSyncData? = null
		coEvery { projectsDatasource.updateSyncData(userId, captureLambda()) } answers {
			lambda<(ProjectsSyncData) -> ProjectsSyncData>().captured.invoke(initialSyncData)
				.apply {
					updatedSyncData = this
				}
		}

		val expectedSyncData = ProjectsSyncData(
			lastSync = Instant.DISTANT_PAST,
			deletedProjects = setOf(
				ProjectId("project-id-2"),
				projectDef.uuid,
			),
		)

		createProjectsRepository().apply {
			val result = deleteProject(userId, syncId, projectId)

			assertTrue(result.isSuccess)
			coVerify { projectEntityDatasource.deleteProject(OwnerId.unchecked(userId), projectDef.uuid) }
			coVerify { projectsDatasource.updateSyncData(userId, any()) }

			assertEquals(expectedSyncData, updatedSyncData)
		}
	}

	@Test
	fun `Delete Project, a co-author's delete is a leave with no tombstone`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getProject(userId, projectId) } returns null
		coEvery { projectDao.getProjectByUuid(projectId) } returns sharedRow(projectId)
		coEvery { projectMemberRepository.leave(sharedRow(projectId), userId) } returns true

		val result = createProjectsRepository().deleteProject(userId, syncId, projectId)

		assertTrue(result.isSuccess)
		coVerify(exactly = 0) { projectEntityDatasource.deleteProject(any(), any()) }
		coVerify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
	}

	@Test
	fun `Delete Project, a stranger's delete is not_member with no tombstone`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getProject(userId, projectId) } returns null
		coEvery { projectDao.getProjectByUuid(projectId) } returns sharedRow(projectId)

		val result = createProjectsRepository().deleteProject(userId, syncId, projectId)

		assertIs<NotMemberException>((result as ServerResult.Failure).exception)
		coVerify(exactly = 0) { projectEntityDatasource.deleteProject(any(), any()) }
		coVerify(exactly = 0) { projectsDatasource.updateSyncData(any(), any()) }
	}

	@Test
	fun `Delete Project, an unknown id is tombstoned for the caller`() = runTest {
		val syncId = "sync-id"
		val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getProject(userId, projectId) } returns null
		coEvery { projectDao.getProjectByUuid(projectId) } returns null
		coEvery { projectsDatasource.updateSyncData(userId, any()) } returns ProjectsSyncData(deletedProjects = emptySet())

		val result = createProjectsRepository().deleteProject(userId, syncId, projectId)

		assertTrue(result.isSuccess)
		coVerify { projectsDatasource.updateSyncData(userId, any()) }
	}
}
