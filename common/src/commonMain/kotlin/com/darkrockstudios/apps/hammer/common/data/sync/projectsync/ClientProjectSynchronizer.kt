package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.BackupOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.CollateIdsOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityDeleteOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okio.IOException
import org.koin.core.component.get
import kotlin.coroutines.cancellation.CancellationException

class ClientProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val entitySynchronizers: EntitySynchronizers,
	private val strRes: StrRes,
	private val syncDataRepository: SyncDataRepository,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val serverProjectApi: ServerProjectApi,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val defaultDispatcher by injectDefaultDispatcher()

	private val operations = listOf(
		projectScope.get<PrepareForSyncOperation>(),
		projectScope.get<FetchLocalDataOperation>(),
		projectScope.get<FetchServerDataOperation>(),
		projectScope.get<CollateIdsOperation>(),
		projectScope.get<BackupOperation>(),
		projectScope.get<IdConflictResolutionOperation>(),
		projectScope.get<EntityDeleteOperation>(),
		projectScope.get<EntityTransferOperation>(),
		projectScope.get<FinalizeSyncOperation>(),
	)

	private val scope = CoroutineScope(defaultDispatcher + SupervisorJob())
	private val conflictResolution = Channel<ApiProjectEntity>()

	val syncCompleteEvent = Channel<Boolean>()

	init {
		scope.launch {
			for (conflict in conflictResolution) {
				entitySynchronizers.handleConflict(conflict)
			}
		}
	}

	suspend fun sync(
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
		onlyNew: Boolean = false,
	): Boolean {
		val initialState = InitialSyncOperationState(onlyNew = onlyNew)
		val result = execute(initialState, onProgress, onLog, onConflict, onComplete)

		syncCompleteEvent.trySend(isSuccess(result))

		return if (isSuccess(result)) {
			Napier.i("Sync completed successfully.")
			true
		} else {
			Napier.e("Sync failed with error: ${result.exception?.message}", result.exception)
			false
		}
	}

	fun resolveConflict(entity: ApiProjectEntity) {
		scope.launch {
			conflictResolution.send(entity)
		}
	}

	suspend fun execute(
		initialState: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
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
					handleSyncFailure(e, onLog, onComplete)

					return result
				}
			}
		} catch (e: Exception) {
			handleSyncFailure(e, onLog, onComplete)

			if (e is CancellationException) throw e

			return CResult.failure(e)
		}

		return CResult.success(currentState)
	}

	private suspend fun handleSyncFailure(
		e: Throwable,
		onLog: OnSyncLog,
		onComplete: suspend () -> Unit
	) {
		Napier.e("Sync failed: ${e.message}", e)
		onLog(
			syncLogE(
				strRes.get(MR.strings.sync_log_entity_failed, e.message ?: "---"),
				projectDef
			)
		)
		endSync()
		onComplete()
	}

	private suspend fun endSync() {
		try {
			val syncId = syncDataRepository.loadSyncData().currentSyncId
				?: throw IllegalStateException("No sync ID")
			val serverProjectId = projectMetadataDatasource.requireProjectId(projectDef)

			val endSyncResult = serverProjectApi.endProjectSync(
				globalSettingsRepository.userIdOrThrow(),
				projectDef.name,
				serverProjectId,
				syncId,
				null,
				null
			)

			if (endSyncResult.isFailure) {
				Napier.e("Failed to end sync", endSyncResult.exceptionOrNull())
			} else {
				val finalSyncData = syncDataRepository.loadSyncData().copy(currentSyncId = null)
				syncDataRepository.saveSyncData(finalSyncData)
			}
		} catch (e: IOException) {
			Napier.e("Sync failed", e)
		} catch (e: IllegalStateException) {
			Napier.e("Sync failed", e)
		}
	}

	companion object {
		const val ENTITY_START = 0.3f
		const val ENTITY_TOTAL = 0.5f
		const val ENTITY_END = ENTITY_START + ENTITY_TOTAL
	}
}