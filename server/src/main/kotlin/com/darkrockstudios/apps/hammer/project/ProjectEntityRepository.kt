package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerImageSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectEntityRepository(
	private val clock: Clock,
	private val projectEntityDatasource: ProjectEntityDatasource,
) : KoinComponent {

	private val sceneSynchronizer: ServerSceneSynchronizer by inject()
	private val noteSynchronizer: ServerNoteSynchronizer by inject()
	private val timelineEventSynchronizer: ServerTimelineSynchronizer by inject()
	private val encyclopediaSynchronizer: ServerEncyclopediaSynchronizer by inject()
	private val sceneDraftSynchronizer: ServerSceneDraftSynchronizer by inject()
	private val imageSynchronizer: ServerImageSynchronizer by inject()

	private val projectsSessions: SyncSessionManager<Long, ProjectsSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	private val sessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECT_SYNC_MANAGER)
	)

	suspend fun beginProjectSync(
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean,
		installId: String? = null,
	): SResult<ProjectSynchronizationBegan> {

		val syncKey = ProjectSyncKey(projectDef.uuid)

		// An in-progress account-level sync (creating/renaming/deleting projects) must not be
		// raced by a project sync, so it still blocks beginning one.
		if (projectsSessions.hasActiveSyncSession(callerId)) {
			return SResult.failure(
				"begin sync failure: existing account session",
				Msg.r("api_project_sync_begin_error_session", callerId)
			)
		}
		if (ownerAccountSyncing(callerId, owner)) return ownerSyncingFailure(callerId, projectDef)

		if (projectEntityDatasource.checkProjectExists(owner, projectDef).not()) {
			return SResult.failure(ProjectNotFound(projectDef))
		}

		// Atomically claim the slot, reclaiming only this install's own stale session
		val newSyncId = sessionManager.claimSession(
			syncKey,
			canReplace = { it.installId == installId },
		) { _: ProjectSyncKey, sync: String ->
			ProjectSynchronizationSession(
				userId = callerId,
				projectDef = projectDef,
				started = clock.now(),
				syncId = sync,
				installId = installId,
			)
		} ?: return SResult.failure(
			"begin sync failure: active session on another install",
			Msg.r("api_project_sync_begin_error_session", callerId)
		)

		// The owner may have begun an account sync since the check above. Their delete, rename and
		// move refuse while this session is live, so checking again after the claim closes the gap.
		if (ownerAccountSyncing(callerId, owner)) {
			sessionManager.terminateSession(syncKey)
			return ownerSyncingFailure(callerId, projectDef)
		}

		var projectSyncData = projectEntityDatasource.loadProjectSyncData(owner, projectDef)

		if (projectSyncData.lastId < 0) {
			val lastId = projectEntityDatasource.findLastId(owner, projectDef)
			projectSyncData = projectSyncData.copy(lastId = lastId ?: -1)
		}

		val updateSequence = getUpdateSequence(owner, projectDef, clientState, lite)
		val syncBegan = ProjectSynchronizationBegan(
			syncId = newSyncId,
			lastId = projectSyncData.lastId,
			lastSync = projectSyncData.lastSync,
			idSequence = updateSequence,
			deletedIds = projectSyncData.deletedIds,
		)
		return SResult.success(syncBegan)
	}

	suspend fun endProjectSync(
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		syncId: String,
		lastSync: Instant?,
		lastId: Int?,
	): SResult<Unit> {
		val syncKey = ProjectSyncKey(projectDef.uuid)
		val session = sessionManager.findSession(syncKey)
		return if (session == null) {
			SResult.failure(
				"begin sync failure: existing session",
				Msg.r("api_project_sync_begin_error_session", callerId)
			)
		} else {
			if (session.syncId != syncId) {
				SResult.failure(
					"end sync failure: invalid session id",
					Msg.r("api_project_sync_end_invalidid", callerId)
				)
			} else {
				// Update sync data if it was sent
				if (lastSync != null && lastId != null) {
					projectEntityDatasource.updateSyncData(owner, projectDef) {
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
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		syncId: String
	): SResult<ProjectServerState> {
		if (validateSyncId(callerId, projectDef, syncId).not())
			return SResult.failure(
				"end sync failure: invalid session id",
				Msg.r("api_project_sync_end_invalidid", callerId)
			)

		return if (projectEntityDatasource.checkProjectExists(owner, projectDef)) {
			val projectSyncData = projectEntityDatasource.loadProjectSyncData(owner, projectDef)
			SResult.success(
				ProjectServerState(
					lastSync = projectSyncData.lastSync,
					lastId = projectSyncData.lastId
				)
			)
		} else {
			SResult.failure(
				"Project does not exist",
				Msg.r("api_project_getproject_error_notfound")
			)
		}
	}

	suspend fun saveEntity(
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entity: ApiProjectEntity,
		originalHash: String?,
		syncId: String,
		force: Boolean
	): SResult<Unit> {
		if (validateSyncId(callerId, projectDef, syncId).not())
			return SResult.failure("Invalid SyncId", exception = InvalidSyncIdException())

		val existingType = projectEntityDatasource.findEntityType(entity.id, owner, projectDef)
		if (existingType != null && existingType != entity.type)
			return SResult.failure(
				"Entity type mismatch",
				exception = EntityTypeConflictException(
					id = entity.id,
					existingType = existingType.toStringId(),
					submittedType = entity.type.toStringId()
				),
				displayMessage = Msg.r("api_project_saveentity_error_typeconflict")
			)

		val result = when (entity) {
			is ApiProjectEntity.SceneEntity -> sceneSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.NoteEntity -> noteSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.TimelineEventEntity -> timelineEventSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.ImageEntity -> imageSynchronizer.saveEntity(
				owner,
				projectDef,
				entity,
				originalHash,
				force
			)
		}
		return result
	}

	suspend fun deleteEntity(
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String,
	): SResult<Unit> {
		if (validateSyncId(callerId, projectDef, syncId).not())
			return SResult.failure("Invalid Sync ID", exception = InvalidSyncIdException())

		val entityType: ApiProjectEntity.Type? =
			projectEntityDatasource.findEntityType(entityId, owner, projectDef)

		val deleteResult = when (entityType) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.NOTE -> noteSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.IMAGE -> imageSynchronizer.deleteEntity(
				owner,
				projectDef,
				entityId
			)

			null -> SResult.success()
		}

		projectEntityDatasource.updateSyncData(owner, projectDef) {
			it.copy(
				deletedIds = it.deletedIds + entityId
			)
		}

		return deleteResult
	}

	suspend fun loadEntity(
		callerId: Long,
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String
	): SResult<ApiProjectEntity> {
		if (validateSyncId(callerId, projectDef, syncId).not())
			return SResult.failure("Invalid sync id", exception = InvalidSyncIdException())

		val type = projectEntityDatasource.findEntityType(entityId, owner, projectDef)
			?: return SResult.failure("EntityNotFound", exception = EntityNotFound(entityId))

		return when (type) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.loadEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.NOTE -> noteSynchronizer.loadEntity(owner, projectDef, entityId)
			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.loadEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.loadEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.loadEntity(
				owner,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.IMAGE -> imageSynchronizer.loadEntity(
				owner,
				projectDef,
				entityId
			)
		}
	}

	/** The owner's account session can rename or delete the project under a co-author. */
	private fun ownerAccountSyncing(callerId: Long, owner: OwnerId): Boolean =
		owner.id != callerId && projectsSessions.hasActiveSyncSession(owner.id)

	private fun ownerSyncingFailure(callerId: Long, projectDef: ProjectDefinition) = SResult.failure<ProjectSynchronizationBegan>(
		"begin sync failure: owner's account session",
		Msg.r("api_project_sync_begin_error_session", callerId),
		OwnerSyncInProgressException(projectDef.uuid),
	)

	/**
	 * Checks the caller's own account session, not the owner's: checking the owner would fail every
	 * co-author request while the owner has the app open.
	 */
	private fun validateSyncId(
		callerId: Long,
		projectDef: ProjectDefinition,
		syncId: String
	): Boolean {
		val syncKey = ProjectSyncKey(projectDef.uuid)
		return !projectsSessions.hasActiveSyncSession(callerId) &&
			sessionManager.validateSyncId(syncKey, syncId)
	}

	private suspend fun getUpdateSequence(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean
	): List<Int> {
		val updateSequence = mutableSetOf<Int>()
		if (lite.not()) {
			// Images first: every other type can reference them.
			updateSequence += imageSynchronizer.getUpdateSequence(owner, projectDef, clientState)
			updateSequence += sceneSynchronizer.getUpdateSequence(owner, projectDef, clientState)
			updateSequence += sceneDraftSynchronizer.getUpdateSequence(
				owner,
				projectDef,
				clientState
			)
			updateSequence += noteSynchronizer.getUpdateSequence(owner, projectDef, clientState)
			updateSequence += encyclopediaSynchronizer.getUpdateSequence(
				owner,
				projectDef,
				clientState
			)
			// After scenes and entries: timeline events reference both.
			updateSequence += timelineEventSynchronizer.getUpdateSequence(
				owner,
				projectDef,
				clientState
			)
		}

		return updateSequence.toList()
	}
}
