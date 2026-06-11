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
	private val syncJournal: SyncJournal,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {

	// Must-not-crash operation boundary; failure wrapped into the returned CResult.
	@Suppress("TooGenericExceptionCaught")
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
			if (serverProjectId == null) {
				return CResult.failure(MissingProjectIdException(projectDef.name))
			}

			val loadedSyncData = syncJournal.loadSyncData()
			val entityState = if (state.onlyNew) {
				null
			} else {
				getEntityState(loadedSyncData)
			}

			// Give every entity a conflict baseline before an upload reads one: a missing baseline
			// uploads as null, which the server lets overwrite a concurrent edit unchecked. An
			// empty syncedHashes map (an upgraded client) leaves entities exposed until backfilled.
			val clientSyncData = if (entityState != null) {
				val backfilled = backfillSyncedHashes(loadedSyncData, entityState)
				if (backfilled !== loadedSyncData) syncJournal.saveSyncData(backfilled)
				backfilled
			} else {
				loadedSyncData
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
			CResult.failure(SyncOperationException(cause = e))
		}
	}

	private suspend fun getEntityState(clientSyncData: ProjectSynchronizationData): ClientEntityState {

		val entities = entitySynchronizers.synchronizers.values.flatMap { syncher ->
			syncher.hashEntities(clientSyncData.newIds)
		}.toSet()

		return ClientEntityState(entities)
	}

	private fun backfillSyncedHashes(
		syncData: ProjectSynchronizationData,
		entityState: ClientEntityState,
	): ProjectSynchronizationData {
		val dirtyById = syncData.dirty.associateBy { it.id }
		val additions = HashMap<Int, String>()
		for (entity in entityState.entities) {
			if (syncData.syncedHashes.containsKey(entity.id)) continue
			val baseline = if (entity.id in dirtyById) {
				// A dirty entity's local hash is the edited content; its baseline is the frozen
				// pre-edit hash from the dirty entry (null when unrecoverable).
				dirtyById.getValue(entity.id).originalHash
			} else {
				// An in-sync entity's local hash equals the server's, so it is a valid baseline.
				entity.hash
			}
			if (baseline != null) additions[entity.id] = baseline
		}
		return if (additions.isEmpty()) {
			syncData
		} else {
			syncData.copy(syncedHashes = syncData.syncedHashes + additions)
		}
	}
}

open class SyncOperationException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class MissingProjectIdException(projectName: String) :
	SyncOperationException("Server project ID missing for: $projectName")