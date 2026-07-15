package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProjectEntityRepositorySyncTest : ProjectEntityRepositoryBaseTest() {

	@Test
	fun `getProjectSyncData with invalid SyncId`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

		ProjectEntityRepository(clock, projectEntityDatasource).apply {
			val result = getProjectSyncData(userId, projectDefinition, "invalid-id")
			assertFalse(result.isSuccess)
		}
	}

	@Test
	fun `Begin Project Sync`() = runTest {
		val syncId = "sync-id"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 5,
			deletedIds = setOf(3, 7)
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery {
			projectEntityDatasource.checkProjectExists(
				userId,
				projectDefinition
			)
		} returns true

		coEvery {
			projectEntityDatasource.loadProjectSyncData(
				userId,
				projectDefinition
			)
		} returns syncData

		mockCreateSession(syncId)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(result))
			val syncBegan = result.data
			assertEquals(syncId, syncBegan.syncId)
			assertEquals(syncData.lastId, syncBegan.lastId)
			assertEquals(syncData.lastSync, syncBegan.lastSync)
			assertEquals(syncData.deletedIds, syncBegan.deletedIds)
		}
	}

	@Test
	fun `Begin Project Sync reclaims the same install's existing session`() = runTest {
		val syncId = "sync-id"
		val installId = "install-1"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 1,
			deletedIds = emptySet()
		)

		// This install already has a leaked/stale session (e.g. a prior sync whose end_sync
		// never reached the server). The owner must be able to begin again rather than being
		// locked out until it expires.
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery {
			projectEntityDatasource.checkProjectExists(userId, projectDefinition)
		} returns true
		coEvery {
			projectEntityDatasource.loadProjectSyncData(userId, projectDefinition)
		} returns syncData

		mockCreateSession(
			syncId,
			existing = ProjectSynchronizationSession(
				userId, projectDefinition, clock.now(), "stale-sync-id", installId
			),
		)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false, installId)

			assertTrue(isSuccess(result))
			// A fresh syncId must be issued, not the stale session's id echoed back.
			assertEquals(syncId, result.data.syncId)
		}
	}

	@Test
	fun `Begin Project Sync is rejected when another install holds an active session`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery {
			projectEntityDatasource.checkProjectExists(userId, projectDefinition)
		} returns true

		mockCreateSession(
			"unused-sync-id",
			existing = ProjectSynchronizationSession(
				userId, projectDefinition, clock.now(), "other-sync-id", "other-install"
			),
		)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false, "install-1")

			assertFalse(isSuccess(result))
		}

		// The other install's session must be left intact.
		verify(exactly = 0) { projectSessionManager.terminateSession(any()) }
	}

	@Test
	fun `Begin Project Sync - Update Sequence - Remove Dupes`() = runTest {
		val syncId = "sync-id"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 1,
			deletedIds = emptySet()
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false

		coEvery {
			projectEntityDatasource.checkProjectExists(
				userId,
				projectDefinition
			)
		} returns true

		coEvery {
			projectEntityDatasource.loadProjectSyncData(
				userId,
				projectDefinition
			)
		} returns syncData

		mockCreateSession(syncId)

		coEvery { sceneSynchronizer.getUpdateSequence(any(), any(), any()) } returns listOf(1, 2, 3)
		coEvery {
			sceneDraftSynchronizer.getUpdateSequence(any(), any(), any())
		} returns listOf(4, 5, 6)
		coEvery { noteSynchronizer.getUpdateSequence(any(), any(), any()) } returns listOf(6, 7, 8)
		coEvery {
			timelineEventSynchronizer.getUpdateSequence(any(), any(), any())
		} returns listOf(8, 9, 10, 11)
		coEvery {
			encyclopediaSynchronizer.getUpdateSequence(any(), any(), any())
		} returns listOf(11, 12, 13, 14)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(result))
			val syncBegan = result.data
			assertEquals(syncId, syncBegan.syncId)
			assertEquals((1..14).toList(), syncBegan.idSequence)
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
				projectEntityDatasource.checkProjectExists(
					userId,
					projectDefinition
				)
			} returns true

			coEvery {
				projectEntityDatasource.loadProjectSyncData(
					userId,
					projectDefinition
				)
			} returns syncData

			var updatedSyncData: ProjectSyncData? = null
			coEvery {
				projectEntityDatasource.updateSyncData(userId, projectDefinition, captureLambda())
			} answers {
				updatedSyncData =
					lambda<(ProjectSyncData) -> ProjectSyncData>().captured.invoke(syncData)
			}

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

			val endLastSync = syncBegan.lastSync + 5.minutes
			val endLastId = 42
			val endResult = endProjectSync(
				userId,
				projectDefinition,
				syncBegan.syncId,
				endLastSync,
				endLastId
			)
			assertTrue { endResult.isSuccess }

			assertEquals(
				syncData.copy(lastSync = endLastSync, lastId = endLastId),
				updatedSyncData
			)
			verify { projectSessionManager.terminateSession(ProjectSyncKey(userId, projectDefinition)) }
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

	@Test
	fun `validateSyncId rejects expired syncIds`() = runTest {
		// The allowExpired parameter is set to false in validateSyncId
		// This test verifies that expired syncIds are rejected
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		// Return false to simulate an expired session when allowExpired=false
		coEvery { projectSessionManager.validateSyncId(any(), any(), false) } returns false

		createProjectRepository().apply {
			val result = getProjectSyncData(userId, projectDefinition, "expired-sync-id")
			assertFalse(result.isSuccess)
		}
	}

	@Test
	fun `getProjectSyncData succeeds with valid non-expired syncId`() = runTest {
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 5,
			deletedIds = setOf(1, 2)
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), false) } returns true
		coEvery {
			projectEntityDatasource.checkProjectExists(userId, projectDefinition)
		} returns true
		coEvery {
			projectEntityDatasource.loadProjectSyncData(userId, projectDefinition)
		} returns syncData

		createProjectRepository().apply {
			val result = getProjectSyncData(userId, projectDefinition, "valid-sync-id")
			assertTrue(isSuccess(result))
			assertEquals(syncData.lastId, result.data.lastId)
			assertEquals(syncData.lastSync, result.data.lastSync)
		}
	}
}