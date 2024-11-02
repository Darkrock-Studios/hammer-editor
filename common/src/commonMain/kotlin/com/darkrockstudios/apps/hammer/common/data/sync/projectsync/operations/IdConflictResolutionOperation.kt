package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.CollateIdsState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import kotlinx.coroutines.yield

class IdConflictResolutionOperation(
	projectDef: ProjectDef,
	private val idRepository: IdRepository,
	private val entitySynchronizers: EntitySynchronizers,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {

		state as CollateIdsState

		// Resolve ID conflicts
		val resolvedClientSyncData =
			handleIdConflicts(state.clientSyncData, state.serverSyncData, onLog)
		val currentMaxId: Int = idRepository.let {
			it.findNextId()
			it.peekLastId()
		}
		val maxId: Int = (resolvedClientSyncData.newIds +
			listOf(currentMaxId, state.serverSyncData.lastId, resolvedClientSyncData.lastId)
			).max()
		val newClientIds = resolvedClientSyncData.newIds

		// Handle IDs newly deleted on server
		for (id in state.collatedIds.serverDeletedIds) {
			deleteEntityLocal(id, onLog)
			yield()
		}

		val newState = IdConflictResolutionState.fromCollateIdsState(
			state.copy(
				clientSyncData = resolvedClientSyncData
			),
			maxId = maxId,
			newClientIds = newClientIds,
		)

		return CResult.success(newState)
	}

	private fun calculateLastClientIdWithoutNewIds(clientSyncData: ProjectSynchronizationData): Int {
		return if (clientSyncData.newIds.isNotEmpty()) {
			clientSyncData.newIds.min() - 1
		} else {
			clientSyncData.lastId
		}
	}

	private suspend fun handleIdConflicts(
		clientSyncData: ProjectSynchronizationData,
		serverSyncData: ProjectSynchronizationBegan,
		onLog: OnSyncLog
	): ProjectSynchronizationData {

		val lastClientIdWithoutNew = calculateLastClientIdWithoutNewIds(clientSyncData)
		return if (serverSyncData.lastId > lastClientIdWithoutNew) {
			if (clientSyncData.newIds.isNotEmpty()) {
				var serverLastId = serverSyncData.lastId
				val updatedNewIds = clientSyncData.newIds.toMutableList()
				val updatedDirty = clientSyncData.dirty.toMutableList()

				val localDeletedIds = clientSyncData.deletedIds.toMutableSet()

				for ((ii, id) in clientSyncData.newIds.withIndex()) {
					if (id <= serverSyncData.lastId) {
						onLog(
							syncLogI(
								"ID $id already exists on server, re-assigning",
								projectDef.name
							)
						)
						val newId = ++serverLastId

						// Re-ID this currently local only Entity
						entitySynchronizers.reIdEntry(id, newId)
						updatedNewIds[ii] = newId

						// If we have a dirty record for this ID, update it
						val dirtyIndex = clientSyncData.dirty.indexOfFirst { it.id == id }
						if (dirtyIndex > -1) {
							updatedDirty[dirtyIndex] =
								clientSyncData.dirty[dirtyIndex].copy(id = newId)
						}

						// If this is a locally deleted ID, update it
						if (localDeletedIds.contains(id)) {
							localDeletedIds.remove(id)
							localDeletedIds.add(newId)
						}
					}
				}

				// Tell ID Repository to re-find the max ID
				idRepository.findNextId()

				clientSyncData.copy(
					newIds = updatedNewIds,
					lastId = updatedNewIds.max(),
					dirty = updatedDirty,
					deletedIds = localDeletedIds
				)
			} else {
				clientSyncData
			}
		} else {
			clientSyncData
		}
	}

	private suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		entitySynchronizers.findById(id)?.deleteEntityLocal(id, onLog)
	}
}