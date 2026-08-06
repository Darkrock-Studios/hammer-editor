package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_project_id_stale
import com.darkrockstudios.apps.hammer.sync_log_server_data_loaded
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first

class FetchServerDataOperation(
	projectDef: ProjectDef,
	private val globalSettingsStore: GlobalSettingsStore,
	private val serverProjectApi: ServerProjectApi,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val projectsRepository: ProjectsRepository,
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
				serverProjectId,
				state.entityState,
				state.onlyNew
			)

		return if (serverSyncDataResult.isSuccess) {
			onProgress(
				0.1f,
				syncLogI(strRes.get(Res.string.sync_log_server_data_loaded), projectDef)
			)

			val fetchServerDataStateState = FetchServerDataState.fromFetchLocalDataState(
				state,
				serverSyncDataResult.getOrThrow()
			)

			CResult.success(fetchServerDataStateState)
		} else {
			val exception =
				serverSyncDataResult.exceptionOrNull() ?: IllegalStateException("No exception")

			// 410 means the server has no record of this id, so it can never succeed. Drop it and
			// EnsureProjectIdOperation creates the project fresh next sync, instead of this
			// failing forever.
			if (exception is HttpFailureException && exception.statusCode == HttpStatusCode.Gone) {
				projectsRepository.removeProjectId(projectDef)
				onLog(syncLogW(strRes.get(Res.string.sync_log_project_id_stale), projectDef))
			}

			CResult.failure(exception)
		}
	}

	private suspend fun userId(): Long {
		return globalSettingsStore.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}