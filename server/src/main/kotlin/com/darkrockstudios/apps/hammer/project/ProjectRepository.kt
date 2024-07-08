package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent

class ProjectRepository(
	private val clock: Clock,
	private val projectDatasource: ProjectDatasource,
) : KoinComponent {

	private val sceneSynchronizer: ServerSceneSynchronizer by inject()
	private val noteSynchronizer: ServerNoteSynchronizer by inject()
	private val timelineEventSynchronizer: ServerTimelineSynchronizer by inject()
	private val encyclopediaSynchronizer: ServerEncyclopediaSynchronizer by inject()
	private val sceneDraftSynchronizer: ServerSceneDraftSynchronizer by inject()

	private val projectsSessions: SyncSessionManager<Long, ProjectsSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	private val sessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECT_SYNC_MANAGER)
	)

	suspend fun beginProjectSync(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean
	): SResult<ProjectSynchronizationBegan> {

		val syncKey = ProjectSyncKey(userId, projectDef)

		return if (
			projectsSessions.hasActiveSyncSession(userId) ||
			sessionManager.hasActiveSyncSession(syncKey)
		) {
			SResult.failure(
				"begin sync failure: existing session",
				Msg.r("api.project.sync.begin.error.session", userId)
			)
		} else {
			if (!projectDatasource.checkProjectExists(userId, projectDef)) {
				projectDatasource.createProject(userId, projectDef)
			}

			var projectSyncData = projectDatasource.loadProjectSyncData(userId, projectDef)

			if (projectSyncData.lastId < 0) {
				val lastId = projectDatasource.findLastId(userId, projectDef)
				projectSyncData = projectSyncData.copy(lastId = lastId ?: -1)
			}

			val newSyncId =
				sessionManager.createNewSession(syncKey) { key: ProjectSyncKey, sync: String ->
					ProjectSynchronizationSession(
						userId = key.userId,
						projectDef = key.projectDef,
						started = clock.now(),
						syncId = sync
					)
				}

			val updateSequence = getUpdateSequence(userId, projectDef, clientState, lite)
			val syncBegan = ProjectSynchronizationBegan(
				syncId = newSyncId,
				lastId = projectSyncData.lastId,
				lastSync = projectSyncData.lastSync,
				idSequence = updateSequence,
				deletedIds = projectSyncData.deletedIds,
			)
			SResult.success(syncBegan)
		}
	}

	suspend fun endProjectSync(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String,
		lastSync: Instant?,
		lastId: Int?,
	): SResult<Unit> {
		val syncKey = ProjectSyncKey(userId, projectDef)
		val session = sessionManager.findSession(syncKey)
		return if (session == null) {
			SResult.failure(
				"begin sync failure: existing session",
				Msg.r("api.project.sync.begin.error.session", userId)
			)
		} else {
			if (session.syncId != syncId) {
				SResult.failure(
					"end sync failure: invalid session id",
					Msg.r("api.project.sync.end.invalidid", userId)
				)
			} else {
				// Update sync data if it was sent
				if (lastSync != null && lastId != null) {
					projectDatasource.updateSyncData(userId, projectDef) {
						it.copy(
							lastSync = lastSync,
							lastId = lastId,
						)
					}
				}

				sessionManager.terminateSession(syncKey)
				SResult.success()
			}
		}
	}

	suspend fun getProjectSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String
	): SResult<ProjectServerState> {
		if (validateSyncId(userId, projectDef, syncId).not())
			return SResult.failure(
				"end sync failure: invalid session id",
				Msg.r("api.project.sync.end.invalidid", userId)
			)

		return if (projectDatasource.checkProjectExists(userId, projectDef)) {
			val projectSyncData = projectDatasource.loadProjectSyncData(userId, projectDef)
			SResult.success(
				ProjectServerState(
					lastSync = projectSyncData.lastSync,
					lastId = projectSyncData.lastId
				)
			)
		} else {
			SResult.failure(
				"Project does not exist",
				Msg.r("api.project.getproject.error.notfound")
			)
		}
	}

	suspend fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: ApiProjectEntity,
		originalHash: String?,
		syncId: String,
		force: Boolean
	): SResult<Boolean> {
		if (validateSyncId(userId, projectDef, syncId).not())
			return SResult.failure("Invalid SyncId", exception = InvalidSyncIdException())

		val existingType = projectDatasource.findEntityType(entity.id, userId, projectDef)
		if (existingType != null && existingType != entity.type)
			return SResult.failure(
				"Entity type mismatch",
				exception = EntityTypeConflictException(
					id = entity.id,
					existingType = existingType,
					submittedType = entity.type
				),
				displayMessage = Msg.r("api.project.saveentity.error.typeconflict")
			)

		val result = when (entity) {
			is ApiProjectEntity.SceneEntity -> sceneSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.NoteEntity -> noteSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.TimelineEventEntity -> timelineEventSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)
		}
		return result
	}

	suspend fun deleteEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String,
	): SResult<Unit> {
		if (validateSyncId(userId, projectDef, syncId).not())
			return SResult.failure("Invalid Sync ID", exception = InvalidSyncIdException())

		projectDatasource.updateSyncData(userId, projectDef) {
			it.copy(
				deletedIds = it.deletedIds + entityId
			)
		}

		val entityType: ApiProjectEntity.Type =
			projectDatasource.findEntityType(entityId, userId, projectDef)
				?: return SResult.failure(
					"No type found",
					exception = NoEntityTypeFound(entityId)
				)

		when (entityType) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.NOTE -> noteSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)
		}

		return SResult.success()
	}

	suspend fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String
	): SResult<ApiProjectEntity> {
		if (validateSyncId(userId, projectDef, syncId).not())
			return SResult.failure("Invalid sync id", exception = InvalidSyncIdException())

		val type = projectDatasource.findEntityType(entityId, userId, projectDef)
			?: return SResult.failure("EntityNotFound", exception = EntityNotFound(entityId))

		return when (type) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.loadEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.NOTE -> noteSynchronizer.loadEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.loadEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.loadEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.loadEntity(
				userId,
				projectDef,
				entityId
			)
		}
	}

	private suspend fun validateSyncId(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String
	): Boolean {
		val syncKey = ProjectSyncKey(userId, projectDef)
		return !projectsSessions.hasActiveSyncSession(userId) &&
			sessionManager.validateSyncId(syncKey, syncId, true)
	}

	private suspend fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean
	): List<Int> {
		val updateSequence = mutableSetOf<Int>()
		if (lite.not()) {
			updateSequence += sceneSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += sceneDraftSynchronizer.getUpdateSequence(
				userId,
				projectDef,
				clientState
			)
			updateSequence += noteSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += timelineEventSynchronizer.getUpdateSequence(
				userId,
				projectDef,
				clientState
			)
			updateSequence += encyclopediaSynchronizer.getUpdateSequence(
				userId,
				projectDef,
				clientState
			)
		}

		return updateSequence.toList()
	}
}
