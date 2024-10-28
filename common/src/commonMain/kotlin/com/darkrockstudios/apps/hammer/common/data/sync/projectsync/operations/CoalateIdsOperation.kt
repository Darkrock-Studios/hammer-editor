package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.CollateIdsState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchServerDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.util.StrRes

class CollateIdsOperation(
	projectDef: ProjectDef,
	private val strRes: StrRes,
	private val syncDataDatasource: SyncDataDatasource,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {

		state as FetchServerDataState

		val newState: CollateIdsState

		state.clientSyncData =
			state.clientSyncData.copy(currentSyncId = state.serverSyncData.syncId)
		state.apply {
			syncDataDatasource.saveSyncData(clientSyncData)

			val combinedDeletions = serverSyncData.deletedIds + clientSyncData.deletedIds
			val serverDeletedIds =
				serverSyncData.deletedIds.filter { clientSyncData.deletedIds.contains(it).not() }
					.toSet()
			val newlyDeletedIds =
				clientSyncData.deletedIds.filter { serverSyncData.deletedIds.contains(it).not() }
					.toSet()
			val dirtyEntities = clientSyncData.dirty.toMutableList()

			newState = CollateIdsState.fromFetchServerDataState(
				state,
				combinedDeletions,
				serverDeletedIds,
				newlyDeletedIds,
				dirtyEntities
			)
		}

		onProgress(
			0.2f,
			syncLogI(strRes.get(MR.strings.sync_log_client_data_loaded), projectDef)
		)

		return CResult.success(newState)
	}
}