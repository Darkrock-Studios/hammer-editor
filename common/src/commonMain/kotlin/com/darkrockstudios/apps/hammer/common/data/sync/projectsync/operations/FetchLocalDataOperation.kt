package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_client_data_computed

class FetchLocalDataOperation(
	projectDef: ProjectDef,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val entitySynchronizers: EntitySynchronizers,
	private val syncDataRepository: SyncDataRepository,
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
			state as InitialSyncOperationState

			val metadata = projectMetadataDatasource.loadMetadata(projectDef)
			val serverProjectId = metadata.info.serverProjectId
				?: error("Server project ID missing for: ${projectDef.name}")

			val clientSyncData = syncDataRepository.loadSyncData()
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
				syncLogI(strRes.get(Res.string.sync_log_client_data_computed), projectDef)
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