package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSessionLock
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SharedProjectParker
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.BackupOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.CollateIdsOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityDeleteOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectNoLongerSharedException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDataSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.WritingActivitySyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_account_project_parked
import com.darkrockstudios.apps.hammer.sync_log_entity_failed
import com.darkrockstudios.apps.hammer.sync_log_project_sync_disabled
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.get
import kotlin.coroutines.cancellation.CancellationException

class ClientProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val entitySynchronizers: EntitySynchronizers,
	private val strRes: StrRes,
	private val syncJournal: SyncJournal,
	private val globalSettingsStore: GlobalSettingsStore,
	private val serverProjectApi: ServerProjectApi,
	private val syncGate: ProjectSyncGateRepository,
	private val projectsRepository: ProjectsRepository,
	private val parker: SharedProjectParker,
	private val sessionLock: AccountSessionLock,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val operations = listOf(
		projectScope.get<PrepareForSyncOperation>(),
		projectScope.get<FetchLocalDataOperation>(),
		projectScope.get<FetchServerDataOperation>(),
		projectScope.get<CollateIdsOperation>(),
		projectScope.get<BackupOperation>(),
		projectScope.get<IdConflictResolutionOperation>(),
		projectScope.get<ProjectDataSyncOperation>(),
		projectScope.get<EntityDeleteOperation>(),
		projectScope.get<EntityTransferOperation>(),
		projectScope.get<WritingActivitySyncOperation>(),
		projectScope.get<FinalizeSyncOperation>(),
	)

	private val conflictResolution = Channel<ApiProjectEntity>()

	val syncCompleteEvent = Channel<Boolean>()

	suspend fun sync(
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit,
		onlyNew: Boolean = false,
		onUnauthorized: suspend () -> Unit,
	): Boolean = coroutineScope {
		val conflictListenerJob = launch {
			for (conflict in conflictResolution) {
				entitySynchronizers.handleConflict(conflict)
			}
		}

		try {
			val initialState = InitialSyncOperationState(onlyNew = onlyNew)
			val result = execute(initialState, onProgress, onLog, onConflict, onComplete, onUnauthorized)

			syncCompleteEvent.trySend(isSuccess(result))

			if (isSuccess(result)) {
				Logger.i("Sync completed successfully.")
				true
			} else {
				Logger.e("Sync failed with error: ${result.exception?.message}", result.exception)
				false
			}
		} finally {
			conflictListenerJob.cancel()
		}
	}

	fun resolveConflict(entity: ApiProjectEntity) {
		conflictResolution.trySend(entity)
	}

	/**
	 * Waits for write operations on the project to finish, and keeps new ones out until it returns.
	 * Refuses a project with sync off on this device without contacting the server; callers are
	 * expected to have checked, this is the backstop.
	 */
	suspend fun execute(
		initialState: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit,
		onUnauthorized: suspend () -> Unit = {}
	): CResult<SyncOperationState> {
		if (!projectsRepository.isSyncEnabled(projectDef)) {
			val reason = if (projectsRepository.getOwner(projectDef) != null) {
				Res.string.sync_log_account_project_parked
			} else {
				Res.string.sync_log_project_sync_disabled
			}
			onLog(syncLogW(strRes.get(reason, projectDef.name), projectDef))
			onComplete(false)
			return CResult.failure(ProjectSyncDisabledException(projectDef.name))
		}
		return syncGate.withSync(projectDef) {
			runOperations(initialState, onProgress, onLog, onConflict, onComplete, onUnauthorized)
		}
	}

	// Must-not-crash sync boundary; failures handled, logged, returned as CResult.
	@Suppress("TooGenericExceptionCaught")
	private suspend fun runOperations(
		initialState: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit,
		onUnauthorized: suspend () -> Unit,
	): CResult<SyncOperationState> {
		var currentState = initialState

		try {
			for (operation in operations) {
				val result =
					operation.execute(currentState, onProgress, onLog, onConflict, onComplete)
				if (isSuccess(result)) {
					currentState = result.data
				} else {
					val e = result.exception ?: error("Project Sync Failed without exception")
					handleSyncFailure(e, onLog, onComplete, onUnauthorized)

					return result
				}
			}
		} catch (e: Exception) {
			handleSyncFailure(e, onLog, onComplete, onUnauthorized)

			if (e is CancellationException) throw e

			return CResult.failure(e)
		}

		return CResult.success(currentState)
	}

	private suspend fun handleSyncFailure(
		e: Throwable,
		onLog: OnSyncLog,
		onComplete: suspend (success: Boolean) -> Unit,
		onUnauthorized: suspend () -> Unit = {}
	) {
		Logger.e("Sync failed: ${e.message}", e)

		onLog(
			syncLogE(
				strRes.get(Res.string.sync_log_entity_failed, e.message ?: "---"),
				projectDef
			)
		)
		endSync()
		// Under the account session lock: parking rewrites sync.json, which account sync also writes.
		if (e.isNoLongerShared() && sessionLock.withSession { parker.park(projectDef) }) {
			onLog(syncLogW(strRes.get(Res.string.sync_log_account_project_parked, projectDef.name), projectDef))
		}
		onComplete(false)

		// Check if the error is a 401 Unauthorized error
		if (e is HttpFailureException && e.statusCode == HttpStatusCode.Unauthorized) {
			onUnauthorized()
		}
	}

	// Runs as cleanup, often from a cancelled sync coroutine
	private suspend fun endSync() = withContext(NonCancellable) {
		try {
			val syncId = syncJournal.loadSyncData().currentSyncId
				?: throw IllegalStateException("No sync ID")

			val endSyncResult = serverProjectApi.endProjectSync(
				globalSettingsStore.userIdOrThrow(),
				projectDef.id,
				syncId,
				null,
				null
			)

			if (endSyncResult.isFailure) {
				Logger.e("Failed to end sync", endSyncResult.exceptionOrNull())
			} else {
				val finalSyncData = syncJournal.loadSyncData().copy(currentSyncId = null)
				syncJournal.saveSyncData(finalSyncData)
			}
		} catch (e: IOException) {
			Logger.e("Sync failed", e)
		} catch (e: IllegalStateException) {
			Logger.e("Sync failed", e)
		}
	}

	companion object {
		const val ENTITY_START = 0.3f
		const val ENTITY_TOTAL = 0.5f
		const val ENTITY_END = ENTITY_START + ENTITY_TOTAL
	}
}

/** A shared project's routes refuse this account, or the server has no record of it. */
private fun Throwable.isNoLongerShared(): Boolean =
	this is ProjectNoLongerSharedException || apiErrorCode() == ApiErrorCode.NOT_MEMBER

class ProjectSyncDisabledException(projectName: String) :
	IllegalStateException("Sync is off for '$projectName' on this device")
