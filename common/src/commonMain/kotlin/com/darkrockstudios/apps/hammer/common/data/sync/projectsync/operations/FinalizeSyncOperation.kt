package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import kotlin.time.Clock
import kotlin.time.Instant

class FinalizeSyncOperation(
	projectDef: ProjectDef,
	private val entitySynchronizers: EntitySynchronizers,
	private val strRes: StrRes,
	private val clock: Clock,
	private val serverProjectApi: ServerProjectApi,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val syncDataDatasource: SyncDataDatasource,
) : SyncOperation(projectDef) {

	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {

		state as EntityTransferState

		var allSuccess = state.allSuccess

		finalizeSync()

		yield()

		onProgress(0.9f, syncLogI(strRes.get(MR.strings.sync_log_finalized), projectDef))

		val newLastId: Int?
		val syncFinishedAt: Instant?
		// If we failed, send up nulls
		if (allSuccess) {
			Napier.d("All success! new maxId: ${state.maxId}")
			newLastId = state.maxId
			syncFinishedAt = clock.now()
		} else {
			newLastId = null
			syncFinishedAt = null
		}

		val endSyncResult = serverProjectApi.endProjectSync(
			userId(),
			projectDef.name,
			state.serverProjectId,
			state.serverSyncData.syncId,
			newLastId,
			syncFinishedAt,
		)

		yield()

		if (endSyncResult.isFailure) {
			Napier.e(strRes.get(MR.strings.sync_log_failed), endSyncResult.exceptionOrNull())
			allSuccess = false
		} else {
			if (allSuccess) {
				onLog(syncLogI(strRes.get(MR.strings.sync_log_data_saved), projectDef))

				// On all success, any dirty entities that weren't processed were not processed because the
				// server felt they didn't need to be, so we can clear them now
				state.collatedIds.dirtyEntities.clear()

				if (newLastId != null && syncFinishedAt != null) {
					val finalSyncData = state.clientSyncData.copy(
						currentSyncId = null,
						lastId = newLastId,
						lastSync = syncFinishedAt,
						dirty = state.collatedIds.dirtyEntities,
						newIds = emptyList(),
						deletedIds = state.collatedIds.combinedDeletions
					)
					syncDataDatasource.saveSyncData(finalSyncData)
				} else {
					onLog(
						syncLogE(
							strRes.get(MR.strings.sync_log_data_save_failed),
							projectDef
						)
					)
				}
			} else {
				onLog(syncLogE(strRes.get(MR.strings.sync_log_data_save_failed), projectDef))
			}
		}

		onProgress(1f, null)

		yield()

		onComplete()

		return if (allSuccess) {
			CResult.success(state)
		} else {
			CResult.failure(SyncFailedException())
		}
	}

	private suspend fun finalizeSync() {
		entitySynchronizers.synchronizers.values.forEach { it.finalizeSync() }
	}

	private suspend fun userId(): Long {
		return globalSettingsRepository.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}

class SyncFailedException : Exception("Sync failed")