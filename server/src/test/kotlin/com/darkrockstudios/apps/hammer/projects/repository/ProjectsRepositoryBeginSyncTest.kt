package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsBeginSyncData
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectsRepositoryBeginSyncTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Begin Sync, conflicting project sync session`() = runTest {
		// Another claimant holds the slot, so the atomic claim returns null
		coEvery { projectsSessionManager.claimSession(any(), any(), any()) } returns null
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		createProjectsRepository().apply {
			val result = beginProjectsSync(userId)
			assertTrue(result.isFailure)
		}
	}

	@Test
	fun `Begin Sync, has projects`() = runTest {
		val newSyncId = "new-sync-id"

		coEvery { projectsSessionManager.claimSession(any(), any(), any()) } returns newSyncId

		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery { projectsDatasource.getProjects(userId) } returns setOf(
			ProjectDefinition("Project 1", ProjectId("uuid-1")),
			ProjectDefinition("Project 2", ProjectId("uuid-2")),
		)

		coEvery { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.fromEpochSeconds(123),
				deletedProjects = setOf(ProjectId("uuid-3"))
			)

		createProjectsRepository().apply {
			val beginResult = beginProjectsSync(userId)
			assertTrue(isSuccess(beginResult))

			val syncData = beginResult.data

			val expectedData = ProjectsBeginSyncData(
				syncId = newSyncId,
				projects = setOf(
					ProjectDefinition("Project 1", ProjectId("uuid-1")),
					ProjectDefinition("Project 2", ProjectId("uuid-2"))
				),
				deletedProjects = setOf(ProjectId("uuid-3"))
			)

			assertEquals(expectedData, syncData)
		}
	}
}