package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.project.EntityTypeConflictException
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProjectEntityRepositoryEntityTest : ProjectEntityRepositoryBaseTest() {

	@Test
	fun `Load Entity - Expired SyncId`() = runTest {
		val entityId = 1
		val syncId = "sync-id"
		val syncData = ProjectSyncData(
			lastSync = clock.now(),
			lastId = 1,
			deletedIds = emptySet()
		)

		mockCreateSession(syncId)

		coEvery { sceneSynchronizer.loadEntity(userId, projectDefinition, entityId) } returns
			SResult.success(createSceneEntity(entityId))

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

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

	@Test
	fun `Load Entity - Scene Entity`() = runTest {
		val syncId = "sync-id"
		val entityId = 1
		val sceneEntity = createSceneEntity(1)

		mockCreateSession(syncId)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true

		coEvery {
			projectEntityDatasource.findEntityType(
				entityId,
				userId,
				projectDefinition
			)
		} returns ApiProjectEntity.Type.SCENE

		coEvery {
			sceneSynchronizer.loadEntity(
				userId,
				projectDefinition,
				entityId,
			)
		} returns SResult.success(sceneEntity)

		createProjectRepository().apply {
			val result = loadEntity(userId, projectDefinition, entityId, syncId)
			assertTrue(isSuccess(result))
			assertEquals(sceneEntity, result.data)
		}
	}

	@Test
	fun `Save Entity - Scene Entity`() = runTest {
		val syncId = "sync-id"
		val sceneEntity = createSceneEntity(1)

		mockCreateSession(syncId)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery {
			projectEntityDatasource.findEntityType(
				any(),
				any(),
				any()
			)
		} returns ApiProjectEntity.Type.SCENE

		coEvery {
			sceneSynchronizer.saveEntity(
				userId,
				projectDefinition,
				sceneEntity,
				null,
				false
			)
		} returns SResult.success(Unit)

		createProjectRepository().apply {
			val result =
				saveEntity(userId, projectDefinition, createSceneEntity(1), null, syncId, false)
			assertTrue(isSuccess(result))
		}

		coVerify {
			sceneSynchronizer.saveEntity(
				userId,
				projectDefinition,
				sceneEntity,
				null,
				false
			)
		}
	}

	@Test
	fun `Save Entity - Incorrect entity type`() = runTest {
		val syncId = "sync-id"
		val entityId = 1
		val noteEntity = createNoteEntity(entityId)

		mockCreateSession(syncId)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true

		coEvery {
			projectEntityDatasource.findEntityType(
				any(),
				any(),
				any()
			)
		} returns ApiProjectEntity.Type.SCENE

		createProjectRepository().apply {
			val result =
				saveEntity(userId, projectDefinition, noteEntity, null, syncId, false)
			assertFalse(isSuccess(result))
			val exception = result.exception
			assertTrue(exception is EntityTypeConflictException)
			assertEquals(ApiProjectEntity.Type.SCENE.toStringId(), exception.existingType)
			assertEquals(ApiProjectEntity.Type.NOTE.toStringId(), exception.submittedType)
			assertEquals(entityId, exception.id)
		}

		coVerify(exactly = 0) { sceneSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Save Entity - No existing entity type`() = runTest {
		val syncId = "sync-id"
		val sceneEntity = createSceneEntity(1)

		mockCreateSession(syncId)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectEntityDatasource.findEntityType(any(), any(), any()) } returns null
		coEvery {
			sceneSynchronizer.saveEntity(
				userId,
				projectDefinition,
				sceneEntity,
				null,
				false
			)
		} returns SResult.success(Unit)

		createProjectRepository().apply {
			val result =
				saveEntity(userId, projectDefinition, sceneEntity, null, syncId, false)
			assertTrue(isSuccess(result))
		}

		coVerify {
			sceneSynchronizer.saveEntity(
				userId,
				projectDefinition,
				sceneEntity,
				null,
				false
			)
		}
	}

	@Test
	fun `Delete Entity - Invalid SyncId`() = runTest {
		val syncId = "sync-id"
		val entityId = 1

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectRepository().apply {
			val result = deleteEntity(userId, projectDefinition, entityId, syncId)
			assertFalse(isSuccess(result))
		}
	}

	@Test
	fun `Delete Entity - Not Found`() = runTest {
		val syncId = "sync-id"
		val entityId = 1

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery { projectEntityDatasource.findEntityType(any(), any(), any()) } returns null
		coEvery { projectEntityDatasource.updateSyncData(any(), any(), any()) } just Runs
		coEvery {
			sceneSynchronizer.deleteEntity(userId, projectDefinition, entityId)
		} returns SResult.success()

		createProjectRepository().apply {
			val result = deleteEntity(userId, projectDefinition, entityId, syncId)
			assertTrue(isSuccess(result))
		}

		coVerify(exactly = 1) { projectEntityDatasource.updateSyncData(any(), any(), any()) }
		coVerify(exactly = 0) { sceneSynchronizer.deleteEntity(any(), any(), any()) }
	}

	@Test
	fun `Delete Entity - Success - SceneEntity`() = runTest {
		val syncId = "sync-id"
		val entityId = 1

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns true
		coEvery {
			projectEntityDatasource.findEntityType(
				any(),
				any(),
				any()
			)
		} returns ApiProjectEntity.Type.SCENE

		val existingDeletedIds = setOf(3)
		lateinit var updatedSyncData: ProjectSyncData
		coEvery { projectEntityDatasource.updateSyncData(any(), any(), captureLambda()) } answers {
			val data = ProjectSyncData(
				lastSync = Instant.fromEpochSeconds(123),
				lastId = 5,
				deletedIds = existingDeletedIds
			)
			updatedSyncData = lambda<(ProjectSyncData) -> ProjectSyncData>().captured.invoke(data)
		}
		coEvery {
			sceneSynchronizer.deleteEntity(userId, projectDefinition, entityId)
		} returns SResult.success()

		createProjectRepository().apply {
			val result = deleteEntity(userId, projectDefinition, entityId, syncId)
			assertTrue(isSuccess(result))
		}

		coVerify { projectEntityDatasource.updateSyncData(userId, projectDefinition, any()) }
		coVerify { sceneSynchronizer.deleteEntity(userId, projectDefinition, entityId) }

		assertEquals(setOf(3, entityId), updatedSyncData.deletedIds)
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

	private fun createNoteEntity(entityId: Int): ApiProjectEntity.NoteEntity {
		return ApiProjectEntity.NoteEntity(
			id = entityId,
			content = "Test Note",
			created = Instant.fromEpochSeconds(123)
		)
	}
}