package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_server_data_loaded
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first

class FetchServerDataOperation(
	projectDef: ProjectDef,
	private val globalSettingsStore: GlobalSettingsStore,
	private val serverProjectApi: ServerProjectApi,
	private val projectCreateResolver: ProjectCreateResolver,
	private val projectsRepository: ProjectsRepository,
	private val syncJournal: SyncJournal,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {

	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit
	): CResult<SyncOperationState> {

		state as FetchLocalDataState

		var result = beginSync(state, state.serverProjectId)

		// 410: the server has no record of this id (it lost the project, or never had it). Create it
		// under the local id and try once more.
		val firstFailure = result.exceptionOrNull()
		if (firstFailure is HttpFailureException && firstFailure.statusCode == HttpStatusCode.Gone) {
			val outcome = projectCreateResolver.createInOwnSession(projectDef)
			logCreateOutcome(outcome, projectDef, strRes, onLog)?.let { return CResult.failure(it) }
			outcome as ServerCreateOutcome.Created

			// Whatever the journal says the server confirmed, this server project does not hold it.
			projectsRepository.clearSyncBaseline(projectDef)
			state.clientSyncData = syncJournal.loadSyncData()
			state.serverProjectId = outcome.projectId

			result = beginSync(state, outcome.projectId)
		}

		return if (result.isSuccess) {
			onProgress(
				0.1f,
				syncLogI(strRes.get(Res.string.sync_log_server_data_loaded), projectDef)
			)

			val fetchServerDataStateState = FetchServerDataState.fromFetchLocalDataState(
				state,
				result.getOrThrow()
			)

			CResult.success(fetchServerDataStateState)
		} else {
			CResult.failure(result.exceptionOrNull() ?: IllegalStateException("No exception"))
		}
	}

	private suspend fun beginSync(
		state: FetchLocalDataState,
		projectId: ProjectId,
	): Result<ProjectSynchronizationBegan> =
		serverProjectApi.beginProjectSync(userId(), projectId, state.entityState, state.onlyNew)

	private suspend fun userId(): Long {
		return globalSettingsStore.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}
