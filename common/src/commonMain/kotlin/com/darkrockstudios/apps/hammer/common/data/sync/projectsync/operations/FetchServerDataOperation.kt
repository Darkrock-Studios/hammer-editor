package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
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
import com.darkrockstudios.apps.hammer.sync_log_account_project_adopt_conflict
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_failure
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_success
import com.darkrockstudios.apps.hammer.sync_log_account_project_deleted_elsewhere
import com.darkrockstudios.apps.hammer.sync_log_account_project_needs_account_sync
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

		var result = beginSync(state)

		// 410: the server has no record of this id (it lost the project, or never had it). Create it
		// under the local id and try once more.
		val firstFailure = result.exceptionOrNull()
		if (firstFailure is HttpFailureException && firstFailure.statusCode == HttpStatusCode.Gone) {
			val outcome = projectCreateResolver.createInOwnSession(projectDef)
			logCreateOutcome(outcome, projectDef, strRes, onLog)?.let { return CResult.failure(it) }

			// Whatever the journal says the server confirmed, this server project does not hold it.
			projectsRepository.clearSyncBaseline(projectDef)
			state.clientSyncData = syncJournal.loadSyncData()
			result = beginSync(state)
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

	private suspend fun beginSync(state: FetchLocalDataState): Result<ProjectSynchronizationBegan> =
		serverProjectApi.beginProjectSync(userId(), projectDef.id, state.entityState, state.onlyNew)

	private suspend fun userId(): Long {
		return globalSettingsStore.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}

/**
 * Logs a [ProjectCreateResolver] outcome for project sync. Returns the error to fail the sync
 * with, or null when the project is on the server.
 */
internal suspend fun logCreateOutcome(
	outcome: ServerCreateOutcome,
	projectDef: ProjectDef,
	strRes: StrRes,
	onLog: OnSyncLog,
): Throwable? = when (outcome) {
	is ServerCreateOutcome.Created -> {
		if (!outcome.alreadyExisted) {
			onLog(
				syncLogI(
					strRes.get(Res.string.sync_log_account_project_create_server_success, projectDef.name),
					projectDef
				)
			)
		}
		null
	}

	ServerCreateOutcome.DeletedElsewhere -> {
		val message = strRes.get(Res.string.sync_log_account_project_deleted_elsewhere, projectDef.name)
		onLog(syncLogW(message, projectDef))
		ProjectDeletedElsewhereException(projectDef.name)
	}

	is ServerCreateOutcome.AdoptConflict -> {
		val message = strRes.get(
			Res.string.sync_log_account_project_adopt_conflict,
			projectDef.name,
			outcome.heldBy.name,
		)
		onLog(syncLogW(message, projectDef))
		ProjectAdoptConflictException(projectDef.name, outcome.heldBy.name)
	}

	ServerCreateOutcome.NeedsAccountSync -> {
		val message = strRes.get(Res.string.sync_log_account_project_needs_account_sync, projectDef.name)
		onLog(syncLogW(message, projectDef))
		ProjectNeedsAccountSyncException(projectDef.name)
	}

	is ServerCreateOutcome.Failed -> {
		onLog(
			syncLogE(
				strRes.get(Res.string.sync_log_account_project_create_server_failure, projectDef.name),
				projectDef
			)
		)
		outcome.error
	}
}

class ProjectDeletedElsewhereException(projectName: String) :
	SyncOperationException("Project was deleted on another device: $projectName")

class ProjectAdoptConflictException(projectName: String, heldBy: String) :
	SyncOperationException("Server project matching $projectName by name is held by $heldBy")

class ProjectNeedsAccountSyncException(projectName: String) :
	SyncOperationException("Project must be synced from the project list to settle its id: $projectName")
