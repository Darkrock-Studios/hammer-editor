package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertTrue

class ProjectsRepositoryEndSyncTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `End Sync, conflicting project sync session`() = runTest {
		val syncId = "sync-id"
		coEvery { projectsSessionManager.findSession(any()) } returns null

		createProjectsRepository().apply {
			val result = endProjectsSync(userId, syncId)
			assertTrue(result.isFailure)
			verify(exactly = 0) { projectsSessionManager.terminateSession(any()) }
		}
	}

	@Test
	fun `End Sync, success`() = runTest {
		val userId = 1L
		val syncId = "sync-id"

		val session = ProjectsSynchronizationSession(
			userId = userId,
			syncId = syncId,
			started = Instant.DISTANT_PAST,
		)
		coEvery { projectsSessionManager.findSession(any()) } returns session
		coEvery { projectsSessionManager.terminateSession(userId) } returns true

		coEvery { projectsSessionManager.createNewSession(any(), any()) } returns syncId

		every { projectsDatasource.getProjects(userId) } returns emptySet()
		every { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)

		createProjectsRepository().apply {
			val result = endProjectsSync(userId, syncId)
			assertTrue(result.isSuccess)

			verify { projectsSessionManager.terminateSession(userId) }
		}
	}
}