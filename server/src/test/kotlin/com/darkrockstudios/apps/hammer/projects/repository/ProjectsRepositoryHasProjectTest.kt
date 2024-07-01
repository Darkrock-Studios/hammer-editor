package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import com.darkrockstudios.apps.hammer.projects.ProjectsBeginSyncData
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryHasProjectTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `hasProject, no project`() = runTest {
		val newSyncId = "new-sync-id"
		val expectedData = ProjectsBeginSyncData(
			syncId = newSyncId,
			projects = emptySet(),
			deletedProjects = emptySet()
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectsSessionManager.createNewSession(any(), any()) } returns newSyncId

		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		every { projectsDatasource.getProjects(userId) } returns emptySet()
		every { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)

		mockCreateSession(newSyncId)

		createProjectsRepository().apply {
			val beginResult = beginProjectsSync(userId)
			assertTrue(isSuccess(beginResult))

			verify(exactly = 1) { projectsDatasource.getProjects(userId) }
			verify(exactly = 1) { projectsDatasource.loadSyncData(userId) }

			val syncData = beginResult.data
			assertEquals(expectedData, syncData)
		}
	}

	@Test
	fun `hasProject, has projects`() = runTest {
		val newSyncId = "new-sync-id"

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectsSessionManager.createNewSession(any(), any()) } returns newSyncId

		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		every { projectsDatasource.getProjects(userId) } returns setOf(
			ProjectDefinition("Project 1"),
			ProjectDefinition("Project 2"),
		)

		every { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.fromEpochSeconds(123),
				deletedProjects = setOf("Project 3")
			)

		mockCreateSession(newSyncId)

		createProjectsRepository().apply {
			val beginResult = beginProjectsSync(userId)
			assertTrue(isSuccess(beginResult))

			val syncData = beginResult.data

			val expectedData = ProjectsBeginSyncData(
				syncId = syncData.syncId,
				projects = setOf(ProjectDefinition("Project 1"), ProjectDefinition("Project 2")),
				deletedProjects = setOf("Project 3")
			)

			assertEquals(expectedData, syncData)
		}
	}
}