package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan

open class SyncOperationState(val onlyNew: Boolean)

open class FetchLocalDataState(
	onlyNew: Boolean = false,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
) : SyncOperationState(onlyNew = onlyNew) {

	companion object {
		fun fromSyncOperationState(
			step1: SyncOperationState,
			clientSyncData: ProjectSynchronizationData,
			entityState: ClientEntityState?,
			serverProjectId: ProjectId,
		): FetchLocalDataState {
			return FetchLocalDataState(
				onlyNew = step1.onlyNew,
				clientSyncData = clientSyncData,
				entityState = entityState,
				serverProjectId = serverProjectId,
			)
		}
	}
}

open class FetchServerDataState(
	onlyNew: Boolean = false,
	clientSyncData: ProjectSynchronizationData,
	entityState: ClientEntityState?,
	serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
) : FetchLocalDataState(
	onlyNew = onlyNew,
	clientSyncData = clientSyncData,
	entityState = entityState,
	serverProjectId = serverProjectId,
) {
	companion object {
		fun fromFetchLocalDataState(
			fetchLocalDataState: FetchLocalDataState,
			serverData: ProjectSynchronizationBegan,
		): FetchServerDataState {
			return FetchServerDataState(
				onlyNew = fetchLocalDataState.onlyNew,
				clientSyncData = fetchLocalDataState.clientSyncData,
				entityState = fetchLocalDataState.entityState,
				serverProjectId = fetchLocalDataState.serverProjectId,
				serverSyncData = serverData
			)
		}
	}
}

open class CollateIdsState(
	onlyNew: Boolean = false,
	clientSyncData: ProjectSynchronizationData,
	entityState: ClientEntityState?,
	serverProjectId: ProjectId,
	serverSyncData: ProjectSynchronizationBegan,
	val collatedIds: CollatedIds,
) : FetchServerDataState(
	onlyNew = onlyNew,
	clientSyncData = clientSyncData,
	entityState = entityState,
	serverProjectId = serverProjectId,
	serverSyncData = serverSyncData,
) {
	companion object {
		fun fromFetchServerDataState(
			step: FetchServerDataState,
			combinedDeletions: Set<Int>,
			serverDeletedIds: Set<Int>,
			newlyDeletedIds: Set<Int>,
			dirtyEntities: MutableList<EntityOriginalState>,
		): CollateIdsState {
			return CollateIdsState(
				onlyNew = step.onlyNew,
				clientSyncData = step.clientSyncData,
				entityState = step.entityState,
				serverProjectId = step.serverProjectId,
				serverSyncData = step.serverSyncData,
				collatedIds = CollatedIds(
					combinedDeletions, serverDeletedIds, newlyDeletedIds, dirtyEntities
				)
			)
		}
	}

	data class CollatedIds(
		val combinedDeletions: Set<Int>,
		val serverDeletedIds: Set<Int>,
		val newlyDeletedIds: Set<Int>,
		val dirtyEntities: MutableList<EntityOriginalState>,
	)
}

open class IdConflictResolutionState(
	onlyNew: Boolean = false,
	clientSyncData: ProjectSynchronizationData,
	entityState: ClientEntityState?,
	serverProjectId: ProjectId,
	serverSyncData: ProjectSynchronizationBegan,
	collateIds: CollatedIds,
	var maxId: Int,
	val newClientIds: List<Int>,
) : CollateIdsState(
	onlyNew = onlyNew,
	clientSyncData = clientSyncData,
	entityState = entityState,
	serverProjectId = serverProjectId,
	serverSyncData = serverSyncData,
	collatedIds = collateIds,
) {
	companion object {
		fun fromCollateIdsState(
			oldState: CollateIdsState,
			maxId: Int,
			newClientIds: List<Int>,
		): IdConflictResolutionState {
			return IdConflictResolutionState(
				onlyNew = oldState.onlyNew,
				clientSyncData = oldState.clientSyncData,
				entityState = oldState.entityState,
				serverProjectId = oldState.serverProjectId,
				serverSyncData = oldState.serverSyncData,
				collateIds = oldState.collatedIds,
				maxId = maxId,
				newClientIds = newClientIds,
			)
		}
	}
}

open class EntityDeleteOperationState(
	onlyNew: Boolean = false,
	clientSyncData: ProjectSynchronizationData,
	entityState: ClientEntityState?,
	serverProjectId: ProjectId,
	serverSyncData: ProjectSynchronizationBegan,
	collateIds: CollatedIds,
	maxId: Int,
	newClientIds: List<Int>,
) : IdConflictResolutionState(
	onlyNew = onlyNew,
	clientSyncData = clientSyncData,
	entityState = entityState,
	serverProjectId = serverProjectId,
	serverSyncData = serverSyncData,
	collateIds = collateIds,
	maxId = maxId,
	newClientIds = newClientIds,
) {
	companion object {
		fun fromIdConflictResolution(
			oldState: IdConflictResolutionState,
		): EntityDeleteOperationState {
			return EntityDeleteOperationState(
				onlyNew = oldState.onlyNew,
				clientSyncData = oldState.clientSyncData,
				entityState = oldState.entityState,
				serverProjectId = oldState.serverProjectId,
				serverSyncData = oldState.serverSyncData,
				collateIds = oldState.collatedIds,
				maxId = oldState.maxId,
				newClientIds = oldState.newClientIds,
			)
		}
	}
}

open class EntityTransferState(
	onlyNew: Boolean = false,
	clientSyncData: ProjectSynchronizationData,
	entityState: ClientEntityState?,
	serverProjectId: ProjectId,
	serverSyncData: ProjectSynchronizationBegan,
	collateIds: CollatedIds,
	maxId: Int,
	newClientIds: List<Int>,
	val allSuccess: Boolean,
) : EntityDeleteOperationState(
	onlyNew = onlyNew,
	clientSyncData = clientSyncData,
	entityState = entityState,
	serverProjectId = serverProjectId,
	serverSyncData = serverSyncData,
	collateIds = collateIds,
	maxId = maxId,
	newClientIds = newClientIds,
) {
	companion object {
		fun fromEntityDeleteOperationState(
			oldState: EntityDeleteOperationState,
			allSuccess: Boolean,
		): EntityTransferState {
			return EntityTransferState(
				onlyNew = oldState.onlyNew,
				clientSyncData = oldState.clientSyncData,
				entityState = oldState.entityState,
				serverProjectId = oldState.serverProjectId,
				serverSyncData = oldState.serverSyncData,
				collateIds = oldState.collatedIds,
				maxId = oldState.maxId,
				newClientIds = oldState.newClientIds,
				allSuccess = allSuccess,
			)
		}
	}
}