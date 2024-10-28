package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchLocalDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.util.StrRes

class FetchLocalDataOperation(
	projectDef: ProjectDef,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val entitySynchronizers: EntitySynchronizers,
	private val syncDataDatasource: SyncDataDatasource,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {

	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		return try {
			val metadata = projectMetadataDatasource.loadMetadata(projectDef)
			val serverProjectId = metadata.info.serverProjectId
				?: error("Server project ID missing for: ${projectDef.name}")

			val clientSyncData = syncDataDatasource.loadSyncData()
			val entityState = if (state.onlyNew) {
				null
			} else {
				getEntityState(clientSyncData)
			}

			val fetchLocalDataState = FetchLocalDataState.fromSyncOperationState(
				state,
				clientSyncData,
				entityState,
				serverProjectId,
			)

			onProgress(
				0.05f,
				syncLogI(strRes.get(MR.strings.sync_log_client_data_computed), projectDef)
			)

			CResult.success(fetchLocalDataState)
		} catch (e: Exception) {
			// TODO probably use a special exception here?
			//state.error = "Failed to fetch local modifications: ${e.message}"
			CResult.failure(e)
		}
	}

	private suspend fun getEntityState(clientSyncData: ProjectSynchronizationData): ClientEntityState {

		val entities = entitySynchronizers.synchronizers.values.flatMap { syncher ->
			syncher.hashEntities(clientSyncData.newIds)
		}.toSet()

		return ClientEntityState(entities)
	}
}