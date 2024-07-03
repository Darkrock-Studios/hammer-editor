package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectRepositorySyncTest : ProjectRepositoryBaseTest() {

	@Test
	fun `getProjectSyncData with invalid SyncId`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

		ProjectRepository(clock, projectDatasource).apply {
			val result = getProjectSyncData(userId, projectDefinition, "invalid-id")
			assertFalse(result.isSuccess)
		}
	}

	@Test
	fun `Begin Project Sync`() = runTest {
		val syncId = "sync-id"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 1,
			deletedIds = emptySet()
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery {
			projectDatasource.checkProjectExists(
				userId,
				projectDefinition
			)
		} returns true

		coEvery {
			projectDatasource.loadProjectSyncData(
				userId,
				projectDefinition
			)
		} returns syncData

		mockCreateSession(syncId)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(result))
			val syncBegan = result.data
			assertTrue(syncBegan.syncId.isNotBlank())
		}
	}

	@Test
	fun `End Project Sync`() = runTest {
		createProjectRepository().apply {

			val syncId = "sync-id"
			val syncData = ProjectSyncData(
				lastSync = clock.now(),
				lastId = 1,
				deletedIds = emptySet()
			)

			coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
			coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false
			coEvery { projectSessionManager.terminateSession(any()) } returns true

			coEvery {
				projectDatasource.checkProjectExists(
					userId,
					projectDefinition
				)
			} returns true

			coEvery {
				projectDatasource.loadProjectSyncData(
					userId,
					projectDefinition
				)
			} returns syncData

			coEvery {
				projectDatasource.updateSyncData(
					userId,
					projectDefinition,
					any(),
				)
			} just Runs

			mockCreateSession(syncId)

			val beginResult = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(beginResult))

			val syncBegan = beginResult.data
			val session = ProjectSynchronizationSession(
				userId,
				projectDefinition,
				clock.now(),
				syncBegan.syncId
			)
			coEvery { projectSessionManager.findSession(any()) } returns session

			val endResult = endProjectSync(
				userId,
				projectDefinition,
				syncBegan.syncId,
				syncBegan.lastSync,
				syncBegan.lastId
			)
			assertTrue { endResult.isSuccess }
		}
	}

	@Test
	fun `End Project Sync - Invalid SyncId`() = runTest {
		coEvery { projectSessionManager.findSession(any()) } returns null

		createProjectRepository().apply {
			val endResult = endProjectSync(userId, projectDefinition, "invalid-id", null, null)
			assertFalse { endResult.isSuccess }
		}
	}
}