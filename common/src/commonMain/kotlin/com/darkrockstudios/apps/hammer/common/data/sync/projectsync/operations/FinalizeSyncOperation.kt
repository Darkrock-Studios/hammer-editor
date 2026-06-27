package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
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
	private val globalSettingsStore: GlobalSettingsStore,
	private val syncDataDatasource: SyncDataDatasource,
	private val projectDataDatasource: ProjectDataDatasource,
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

		onProgress(0.9f, syncLogI(strRes.get(Res.string.sync_log_finalized), projectDef))

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
			state.serverProjectId,
			state.serverSyncData.syncId,
			newLastId,
			syncFinishedAt,
		)

		yield()

		if (endSyncResult.isFailure) {
			Napier.e(strRes.get(Res.string.sync_log_failed), endSyncResult.exceptionOrNull())
			allSuccess = false
		} else {
			if (allSuccess) {
				onLog(syncLogI(strRes.get(Res.string.sync_log_data_saved), projectDef))

				// On all success, any dirty entities that weren't processed were not processed because the
				// server felt they didn't need to be, so we can clear them now
				state.collatedIds.dirtyEntities.clear()

				if (newLastId != null && syncFinishedAt != null) {
					// syncedHashes are written to disk per-entity during transfer; reload them here
					// so this final write (built from the sync-start snapshot) doesn't clobber them.
					// dirty/newIds come from the in-memory state, which holds the id-conflict
					// remapping. Prune deleted ids (incl. server-driven deletions that bypass
					// recordIdDeletion) so stale baselines can't mis-fire if an id is reused.
					val persistedSyncedHashes = syncDataDatasource.loadSyncData().syncedHashes -
						state.collatedIds.combinedDeletions
					// Cache the project-wide hash of the now-reconciled local state so the next
					// app open can probe-skip this project if nothing changed. Computed from the
					// final state, not the pre-sync snapshot, since entity transfer may have changed it.
					val projectHash = computeProjectHash()
					val finalSyncData = state.clientSyncData.copy(
						currentSyncId = null,
						lastId = newLastId,
						lastSync = syncFinishedAt,
						dirty = state.collatedIds.dirtyEntities,
						newIds = emptyList(),
						deletedIds = state.collatedIds.combinedDeletions,
						syncedHashes = persistedSyncedHashes,
						cachedProjectHash = projectHash,
						hashAlgoVersion = ProjectContentHasher.ALGO_VERSION,
					)
					syncDataDatasource.saveSyncData(finalSyncData)
				} else {
					onLog(
						syncLogE(
							strRes.get(Res.string.sync_log_data_save_failed),
							projectDef
						)
					)
				}
			} else {
				onLog(syncLogE(strRes.get(Res.string.sync_log_data_save_failed), projectDef))
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

	private suspend fun computeProjectHash(): String {
		val entityHashes = entitySynchronizers.synchronizers.values
			.flatMap { it.hashEntities(emptyList()) }
			.toSet()
		val projectDataHash = ProjectDataHasher.hash(projectDataDatasource.load().data)
		return ProjectContentHasher.hash(entityHashes, projectDataHash)
	}

	private suspend fun userId(): Long {
		return globalSettingsStore.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}
}

class SyncFailedException : Exception("Sync failed")