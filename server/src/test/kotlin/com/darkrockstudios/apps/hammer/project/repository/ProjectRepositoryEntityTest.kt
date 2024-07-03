package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProjectRepositoryEntityTest : ProjectRepositoryBaseTest() {

	@Test
	fun `loadEntity - Expired SyncId`() = runTest {
		val entityId = 1
		val syncId = "sync-id"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 1,
			deletedIds = emptySet()
		)

		mockCreateSession(syncId)

		every { sceneSynchronizer.loadEntity(userId, projectDefinition, entityId) } returns
			SResult.success(createSceneEntity(entityId))

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

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

		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition, clientState, false)
			assertTrue(isSuccess(beginResult))

			val syncBegan = beginResult.data
			clock.advanceTime(SynchronizationSession.EXPIRATION_TIME + 1.minutes)

			val result = loadEntity(userId, projectDefinition, 1, syncBegan.syncId)
			assertTrue(isFailure(result))

			val exception = result.exception
			assertTrue(exception is InvalidSyncIdException)
		}
	}

	private fun createSceneEntity(entityId: Int): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = entityId,
			sceneType = ApiSceneType.Scene,
			name = "Test Scene",
			order = 1,
			path = emptyList(),
			content = "Test Content",
			outline = "",
			notes = "",
		)
	}
}