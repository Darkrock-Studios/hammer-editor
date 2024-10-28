package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchLocalDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchServerDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.flow.first

class FetchServerDataOperation(
	projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
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

		state as FetchLocalDataState

		val metadata = projectMetadataDatasource.loadMetadata(projectDef)
		val serverProjectId = metadata.info.serverProjectId
			?: error("Server project ID missing for: ${projectDef.name}")

		val serverSyncDataResult =
			serverProjectApi.beginProjectSync(
				userId(),
				projectDef.name,
				serverProjectId,
				state.entityState,
				state.onlyNew
			)

		return if (serverSyncDataResult.isSuccess) {
			onProgress(
				0.1f,
				syncLogI(strRes.get(MR.strings.sync_log_server_data_loaded), projectDef)
			)

			val fetchServerDataStateState = FetchServerDataState.fromFetchLocalDataState(
				state,
				serverSyncDataResult.getOrThrow()
			)

			CResult.success(fetchServerDataStateState)
		} else {
			CResult.failure(
				serverSyncDataResult.exceptionOrNull() ?: IllegalStateException("No exception")
			)
		}
	}

	private suspend fun userId(): Long {
		return globalSettingsRepository.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}