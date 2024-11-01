package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan

interface SyncOperationState

data class InitialSyncOperationState(
	val onlyNew: Boolean
) : SyncOperationState

data class FetchLocalDataState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
) : SyncOperationState {

	companion object {
		fun fromSyncOperationState(
			step1: InitialSyncOperationState,
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

data class FetchServerDataState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
) : SyncOperationState {
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

data class CollateIdsState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
	val collatedIds: CollatedIds,
) : SyncOperationState {
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

data class IdConflictResolutionState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
	var collatedIds: CollateIdsState.CollatedIds,
	var maxId: Int,
	val newClientIds: List<Int>,
) : SyncOperationState {
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
				collatedIds = oldState.collatedIds,
				maxId = maxId,
				newClientIds = newClientIds,
			)
		}
	}
}

open class EntityDeleteOperationState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
	var collatedIds: CollateIdsState.CollatedIds,
	var maxId: Int,
	var newClientIds: List<Int>,
) : SyncOperationState {
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
				collatedIds = oldState.collatedIds,
				maxId = oldState.maxId,
				newClientIds = oldState.newClientIds,
			)
		}
	}
}

data class EntityTransferState(
	val onlyNew: Boolean,
	var clientSyncData: ProjectSynchronizationData,
	var entityState: ClientEntityState?,
	var serverProjectId: ProjectId,
	var serverSyncData: ProjectSynchronizationBegan,
	var collatedIds: CollateIdsState.CollatedIds,
	var maxId: Int,
	var newClientIds: List<Int>,
	val allSuccess: Boolean,
) : SyncOperationState {
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
				collatedIds = oldState.collatedIds,
				maxId = oldState.maxId,
				newClientIds = oldState.newClientIds,
				allSuccess = allSuccess,
			)
		}
	}
}