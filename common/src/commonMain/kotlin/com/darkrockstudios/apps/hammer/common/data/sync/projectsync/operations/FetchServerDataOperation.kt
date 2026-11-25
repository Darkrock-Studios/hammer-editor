package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_account_project_adopt_conflict
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_failure
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_success
import com.darkrockstudios.apps.hammer.sync_log_account_project_deleted_elsewhere
import com.darkrockstudios.apps.hammer.sync_log_account_project_needs_account_sync
import com.darkrockstudios.apps.hammer.sync_log_project_sync_busy_gave_up
import com.darkrockstudios.apps.hammer.sync_log_project_sync_busy_waiting
import com.darkrockstudios.apps.hammer.sync_log_server_data_loaded
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FetchServerDataOperation(
	projectDef: ProjectDef,
	private val globalSettingsStore: GlobalSettingsStore,
	private val serverProjectApi: ServerProjectApi,
	private val projectCreateResolver: ProjectCreateResolver,
	private val projectDataRepository: ProjectDataRepository,
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

		var result = beginSyncWhenFree(state, onLog)

		// 410: the server has no record of this id (it lost the project, or never had it). Create it
		// under the local id and try once more.
		val firstFailure = result.exceptionOrNull()
		if (firstFailure is HttpFailureException && firstFailure.statusCode == HttpStatusCode.Gone) {
			val outcome = projectCreateResolver.createInOwnSession(projectDef)
			logCreateOutcome(outcome, projectDef, strRes, onLog)?.let { return CResult.failure(it) }

			// Whatever the journal says the server confirmed, this server project does not hold it.
			syncJournal.clearSyncBaseline()
			projectDataRepository.clearSyncBaseline()
			state.clientSyncData = syncJournal.loadSyncData()
			result = beginSyncWhenFree(state, onLog)
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
			val failure = result.exceptionOrNull()
			if (failure.isProjectSyncBusy()) {
				// Still busy after waiting: fail this sync only. Nothing local is lost; the
				// changes stay unsynced and go up on the next sync.
				onLog(syncLogW(strRes.get(Res.string.sync_log_project_sync_busy_gave_up, projectDef.name), projectDef))
				CResult.failure(ProjectSyncBusyException(projectDef.name))
			} else {
				CResult.failure(failure ?: IllegalStateException("No exception"))
			}
		}
	}

	/**
	 * Begins the sync, waiting while the server says the project is busy: another device or a
	 * co-author is syncing it, as when two copies of a book are closed at the same moment. Tries
	 * again after each of [PROJECT_SYNC_BUSY_WAITS], then returns the last answer.
	 */
	private suspend fun beginSyncWhenFree(
		state: FetchLocalDataState,
		onLog: OnSyncLog,
	): Result<ProjectSynchronizationBegan> {
		var result = beginSync(state)
		for ((attempt, wait) in PROJECT_SYNC_BUSY_WAITS.withIndex()) {
			if (!result.exceptionOrNull().isProjectSyncBusy()) break
			if (attempt == 0) {
				onLog(syncLogI(strRes.get(Res.string.sync_log_project_sync_busy_waiting, projectDef.name), projectDef))
			}
			delay(wait)
			result = beginSync(state)
		}
		return result
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

	ServerCreateOutcome.SharedProject -> ProjectNoLongerSharedException(projectDef.name)

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

/** The waits between attempts to begin a sync while the project is busy: about a minute in all. */
internal val PROJECT_SYNC_BUSY_WAITS: List<Duration> =
	listOf(3.seconds, 5.seconds, 10.seconds, 15.seconds, 15.seconds, 15.seconds)

private fun Throwable?.isProjectSyncBusy(): Boolean =
	this is HttpFailureException && error.errorCode == ApiErrorCode.PROJECT_SYNC_IN_PROGRESS

/** The project was still being synced elsewhere after waiting; the local changes go up next time. */
class ProjectSyncBusyException(projectName: String) :
	SyncOperationException("Project is still being synced by another device or co-author: $projectName")

class ProjectDeletedElsewhereException(projectName: String) :
	SyncOperationException("Project was deleted on another device: $projectName")

class ProjectAdoptConflictException(projectName: String, heldBy: String) :
	SyncOperationException("Server project matching $projectName by name is held by $heldBy")

/** The server no longer has a shared project for this account, which must not create it as its own. */
class ProjectNoLongerSharedException(projectName: String) :
	SyncOperationException("Project is no longer shared with this account: $projectName")

class ProjectNeedsAccountSyncException(projectName: String) :
	SyncOperationException("Project must be synced from the project list to settle its id: $projectName")
