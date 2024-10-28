package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.requireProjectId
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityDeleteOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier

class EntityDeleteOperation(
	projectDef: ProjectDef,
	private val serverProjectApi: ServerProjectApi,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		state as IdConflictResolutionState

		val successfullyDeletedIds = mutableSetOf<Int>()
		// Handle IDs newly deleted on client
		for (id in state.collatedIds.newlyDeletedIds) {
			if (deleteEntityRemote(id, state.serverSyncData.syncId, onLog)) {
				successfullyDeletedIds.add(id)
			}
		}
		val failedDeletes = state.collatedIds.newlyDeletedIds
			.filter { successfullyDeletedIds.contains(it).not() }
			.toSet()
		if (failedDeletes.isNotEmpty()) {
			Napier.d("Failed to Delete IDs: ${failedDeletes.joinToString(",")}")
		}

		// Remove any dirty that were deleted
		state.collatedIds.combinedDeletions.forEach { deletedId ->
			state.collatedIds.dirtyEntities.find { it.id == deletedId }?.let { deleted ->
				state.collatedIds.dirtyEntities.remove(deleted)
			}
		}

		val newState = EntityDeleteOperationState.fromIdConflictResolution(
			oldState = state,
		)

		return CResult.success(newState)
	}

	private suspend fun deleteEntityRemote(id: Int, syncId: String, onLog: OnSyncLog): Boolean {
		val projectId = projectMetadataDatasource.requireProjectId(projectDef)
		val result = serverProjectApi.deleteId(projectDef.name, projectId, id, syncId)
		return if (result.isSuccess) {
			onLog(syncLogI(strRes.get(MR.strings.sync_log_entity_delete_success, id), projectDef))
			true
		} else {
			val message = result.exceptionOrNull()?.message

			onLog(
				syncLogE(
					strRes.get(
						MR.strings.sync_log_entity_delete_failed,
						id,
						message ?: "---"
					), projectDef
				)
			)
			false
		}
	}
}