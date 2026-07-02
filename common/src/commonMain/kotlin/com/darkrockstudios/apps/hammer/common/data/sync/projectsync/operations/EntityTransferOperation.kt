package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_END
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_START
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_TOTAL
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityDeleteOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityTransferState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.toEntityType
import com.darkrockstudios.apps.hammer.common.server.EntityNotFoundException
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.StaleServerHashException
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_entities_transferred
import com.darkrockstudios.apps.hammer.sync_log_entity_conflict
import com.darkrockstudios.apps.hammer.sync_log_entity_delete_failed
import com.darkrockstudios.apps.hammer.sync_log_entity_delete_success
import com.darkrockstudios.apps.hammer.sync_log_entity_download_failed_general
import com.darkrockstudios.apps.hammer.sync_log_entity_download_failed_not_found
import com.darkrockstudios.apps.hammer.sync_log_entity_download_not_modified
import com.darkrockstudios.apps.hammer.sync_log_entity_download_rejected_mismatch
import com.darkrockstudios.apps.hammer.sync_log_entity_download_success
import com.darkrockstudios.apps.hammer.sync_log_entity_upload_entity_not_owned
import com.darkrockstudios.apps.hammer.sync_log_stale_hash_detected
import com.darkrockstudios.apps.hammer.sync_log_stale_hash_heal_failed
import com.darkrockstudios.apps.hammer.sync_log_stale_hash_healed
import io.github.aakira.napier.Napier
import kotlinx.coroutines.yield

class EntityTransferOperation(
	projectDef: ProjectDef,
	private val strRes: StrRes,
	private val entitySynchronizers: EntitySynchronizers,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val serverProjectApi: ServerProjectApi,
	private val syncJournal: SyncJournal,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		state as EntityDeleteOperationState

		onProgress(ENTITY_START, null)

		val transferState = TransferState(
			maxId = state.maxId,
			combinedDeletions = state.collatedIds.combinedDeletions,
			resolvedClientSyncData = state.clientSyncData,
			serverSyncData = state.serverSyncData,
			newClientIds = state.newClientIds,
			dirtyEntities = state.collatedIds.dirtyEntities
		)

		// Transfer Entities
		val allSuccess = if (state.onlyNew) {
			uploadNewEntities(
				state.newClientIds,
				state.serverSyncData,
				state.collatedIds.dirtyEntities,
				onProgress,
				onLog
			)
		} else {
			fullEntityTransfer(
				transferState,
				onProgress,
				onLog,
				onConflict
			)
		}

		onProgress(
			ENTITY_END,
			syncLogI(strRes.get(Res.string.sync_log_entities_transferred), projectDef)
		)

		val newState = EntityTransferState.fromEntityDeleteOperationState(state, allSuccess)

		return CResult.success(newState)
	}

	private suspend fun uploadNewEntities(
		newClientIds: List<Int>,
		serverSyncData: ProjectSynchronizationBegan,
		dirtyEntities: MutableList<EntityOriginalState>,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog
	): Boolean {
		var allSuccess = true

		suspend fun onConflict(entity: ApiProjectEntity) {
			val message = strRes.get(Res.string.sync_log_entity_conflict, entity.id, entity.type)
			onLog(syncLogE(message, projectDef))
			throw IllegalStateException(message)
		}

		val total = newClientIds.size - 1

		newClientIds.forEachIndexed { index, thisId ->
			val success = uploadEntity(thisId, serverSyncData.syncId, null, ::onConflict, onLog)
			if (success) {
				dirtyEntities.find { it.id == thisId }?.let { dirty ->
					dirtyEntities.remove(dirty)
				}
			}
			allSuccess = allSuccess && success
			onProgress(ENTITY_START + (ENTITY_TOTAL * (index / total.toFloat())), null)

			yield()
		}

		return allSuccess
	}

	private suspend fun fullEntityTransfer(
		state: TransferState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>
	): Boolean {
		var allSuccess = true

		// Add dirty IDs that are not already in the update sequence
		val dirtyEntityIds = state.dirtyEntities
			.map { it.id }
			.filter { id -> !state.serverSyncData.idSequence.contains(id) }
		// Add local IDs on top of the server sequence
		val combinedSequence = if (state.maxId > state.serverSyncData.lastId) {
			val localIds = (state.serverSyncData.lastId + 1..state.maxId).toList()
			state.serverSyncData.idSequence + localIds
		} else {
			state.serverSyncData.idSequence
		}.toSet()

		val totalIds = combinedSequence.size
		var currentIndex = 0

		for (thisId in combinedSequence) {
			++currentIndex
			if (thisId in state.combinedDeletions) {
				Napier.d("Skipping deleted ID $thisId")
				continue
			}
			//Napier.d("Syncing ID $thisId")

			val localIsDirty = state.resolvedClientSyncData.dirty.find { it.id == thisId }
			val isNewlyCreated = state.newClientIds.contains(thisId)
			val clientHasEntity = entitySynchronizers.clientHasEntity(thisId)

			// If our copy is dirty, or this ID hasn't been seen by the server yet
			allSuccess =
				if (clientHasEntity && (isNewlyCreated || (localIsDirty != null || thisId > state.serverSyncData.lastId))) {
					Napier.d("Upload ID $thisId (clientHasEntity: $clientHasEntity isNewlyCreated: $isNewlyCreated localIsDirty: $localIsDirty thisId: $thisId Server Last ID: ${state.serverSyncData.lastId})")
					// Source the conflict baseline from the server-confirmed hash, not the dirty
					// entry's frozen copy: a partial sync (this entity uploaded, a later one failed,
					// so finalize never ran to clear the dirty list) advances syncedHashes but leaves
					// the dirty entry stale. On retry the stale baseline would forge a phantom conflict.
					val originalHash = state.resolvedClientSyncData.syncedHashes[thisId]
					val success =
						uploadEntity(
							thisId,
							state.serverSyncData.syncId,
							originalHash,
							onConflict,
							onLog
						)

					if (success) {
						state.dirtyEntities.find { it.id == thisId }?.let { dirty ->
							state.dirtyEntities.remove(dirty)
						}
					} else {
						Napier.d("Upload failed for ID $thisId")
					}

					allSuccess && success
				}
				// Otherwise download the server's copy
				else {
					Napier.d("Download ID $thisId")
					val downloadSuccess = downloadEntry(
						thisId,
						state.serverSyncData.syncId,
						onLog,
					)
					val isFinalSuccess = if (isFailure(downloadSuccess)) {
						if (downloadSuccess.exception is EntityNotFoundException) {
							val entityId = downloadSuccess.exception.entityId
							val entityExistsLocally =
								(entitySynchronizers.findEntityType(entityId) != null)
							if (entityExistsLocally.not()) {
								Napier.i("Entity ID $entityId missing from both client and server, marking it as deleted")
								deleteEntityRemote(thisId, state.serverSyncData.syncId, onLog)
								state.combinedDeletions += entityId
								true
							} else {
								// TODO what do we do here?
								Napier.w("Entity ID $entityId missing from server, but it does exist locally, should we upload it? How did we get here?")
								false
							}
						} else {
							Napier.d("Download failed for ID $thisId")
							false
						}
					} else {
						downloadSuccess.isSuccess
					}
					allSuccess && isFinalSuccess
				}
			onProgress(ENTITY_START + (ENTITY_TOTAL * (currentIndex / totalIds.toFloat())), null)

			yield()
		}

		return allSuccess
	}

	private suspend fun downloadEntry(
		id: Int,
		syncId: String,
		onLog: OnSyncLog
	): CResult<Unit> {
		val localEntityHash = entitySynchronizers.getLocalEntityHash(id)
		val serverProjectId = projectMetadataDatasource.requireProjectId(projectDef)
		val entityResponse = serverProjectApi.downloadEntity(
			projectId = serverProjectId,
			entityId = id,
			syncId = syncId,
			localHash = localEntityHash
		)

		return if (entityResponse.isSuccess) {
			val serverEntity = entityResponse.getOrThrow().entity

			// The server is asked for a specific id but the response carries its own id and
			// type. Never trust either: a hostile server could forge a different entity over
			// the requested one and cement the forgery as the conflict baseline. Reject any
			// id mismatch outright, and any type mismatch when the client already owns the id.
			val localType = entitySynchronizers.findEntityType(id)
			val serverType = serverEntity.type.toEntityType()
			if (serverEntity.id != id || (localType != null && localType != serverType)) {
				onLog(
					syncLogE(
						strRes.get(Res.string.sync_log_entity_download_rejected_mismatch, id),
						projectDef
					)
				)
				return CResult.failure(
					IllegalStateException("Server returned mismatched entity for requested id $id")
				)
			}

			val success = when (serverEntity) {
				is ApiProjectEntity.SceneEntity ->
					entitySynchronizers.sceneSynchronizer.storeEntity(
						serverEntity,
						syncId,
						onLog
					)

				is ApiProjectEntity.NoteEntity ->
					entitySynchronizers.noteSynchronizer.storeEntity(
						serverEntity,
						syncId,
						onLog
					)

				is ApiProjectEntity.TimelineEventEntity -> entitySynchronizers.timelineSynchronizer
					.storeEntity(
						serverEntity,
						syncId,
						onLog
					)

				is ApiProjectEntity.EncyclopediaEntryEntity ->
					entitySynchronizers.encyclopediaSynchronizer.storeEntity(
						serverEntity,
						syncId,
						onLog
					)

				is ApiProjectEntity.SceneDraftEntity ->
					entitySynchronizers.sceneDraftSynchronizer.storeEntity(
						serverEntity,
						syncId,
						onLog
					)
			}

			if (success) {
				// Lock the downloaded entity's hash in as the conflict baseline: it's exactly
				// what the server holds, so a later local edit won't forge a phantom conflict.
				syncJournal.recordSyncedHash(id, serverEntity.hash())
				healServerIfEnriched(id, serverEntity, syncId, onLog)
				onLog(
					syncLogI(
						strRes.get(Res.string.sync_log_entity_download_success, id),
						projectDef
					)
				)
				CResult.success()
			} else {
				onLog(
					syncLogE(
						strRes.get(Res.string.sync_log_entity_download_failed_general, id),
						projectDef
					)
				)
				CResult.failure(IllegalStateException("Failed to store downloaded entity $id"))
			}
		} else {
			when (val exception = entityResponse.exceptionOrNull()) {
				is EntityNotModifiedException -> {
					onLog(
						syncLogI(
							strRes.get(Res.string.sync_log_entity_download_not_modified, id),
							projectDef
						)
					)
					CResult.success()
				}

				is EntityNotFoundException -> {
					onLog(
						syncLogW(
							strRes.get(
								Res.string.sync_log_entity_download_failed_not_found,
								id
							), projectDef
						)
					)
					CResult.failure(EntityNotFoundException(id))
				}

				is StaleServerHashException -> {
					onLog(
						syncLogW(
							strRes.get(Res.string.sync_log_stale_hash_detected, id),
							projectDef
						)
					)
					Napier.w("Stale server hash detected for entity $id. Cached: ${exception.cachedHash}, Computed: ${exception.computedHash}")

					// Heal the server by force uploading our local copy. Legacy servers
					// (pre read-repair) refuse the download until healed, so without a
					// local copy to upload this is a real failure, not a heal.
					suspend fun onConflict(entity: ApiProjectEntity) {
						val message = strRes.get(Res.string.sync_log_entity_conflict, entity.id, entity.type)
						onLog(syncLogE(message, projectDef))
						throw IllegalStateException(message)
					}

					val uploadSuccess = entitySynchronizers.clientHasEntity(id) &&
						uploadEntity(id, syncId, null, ::onConflict, onLog, force = true)
					if (uploadSuccess) {
						onLog(
							syncLogI(
								strRes.get(Res.string.sync_log_stale_hash_healed, id),
								projectDef
							)
						)
						CResult.success()
					} else {
						onLog(
							syncLogE(
								strRes.get(Res.string.sync_log_stale_hash_heal_failed, id),
								projectDef
							)
						)
						CResult.failure(IllegalStateException("Failed to heal stale server hash"))
					}
				}

				else -> {
					val message = strRes.get(Res.string.sync_log_entity_download_failed_general, id)
					Napier.e(message, exception)
					onLog(syncLogE(message, projectDef))
					CResult.failure(
						exception ?: IllegalStateException("Unknown error")
					)
				}
			}
		}
	}

	/**
	 * When storing a downloaded entity leaves the local copy hashing differently from the server's
	 * (e.g. a null field the client backfilled on store), push the enriched copy back so the server
	 * converges — otherwise it re-sends the same entity on every sync. The hash just recorded as the
	 * baseline is exactly what the server holds, so a lone client heals without a conflict.
	 */
	private suspend fun healServerIfEnriched(
		id: Int,
		serverEntity: ApiProjectEntity,
		syncId: String,
		onLog: OnSyncLog,
	) {
		val localHash = entitySynchronizers.getLocalEntityHash(id) ?: return
		if (localHash == serverEntity.hash()) return

		Napier.d("Healing server entity $id: local copy is enriched beyond the server's")
		suspend fun abortOnConflict(entity: ApiProjectEntity): Unit = throw HealConflictException(entity.id)
		try {
			uploadEntity(id, syncId, originalHash = serverEntity.hash(), ::abortOnConflict, onLog)
		} catch (e: HealConflictException) {
			Napier.w("Heal upload for entity ${e.entityId} hit a conflict; deferring to a later sync")
		}
	}

	private suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onLog: OnSyncLog,
		force: Boolean = false
	): Boolean {
		val type: EntityType? = entitySynchronizers.findEntityType(id)
		return if (type != null) {
			entitySynchronizers[type].uploadEntity(
				id, syncId, originalHash, onConflict, onLog, force,
				onSynced = { syncedId, hash -> syncJournal.recordSyncedHash(syncedId, hash) },
			)
		} else {
			onLog(
				syncLogW(
					strRes.get(Res.string.sync_log_entity_upload_entity_not_owned, id),
					projectDef
				)
			)
			true
		}
	}

	private suspend fun deleteEntityRemote(id: Int, syncId: String, onLog: OnSyncLog): Boolean {
		val projectId = projectMetadataDatasource.requireProjectId(projectDef)
		val result = serverProjectApi.deleteId(projectId, id, syncId)
		return if (result.isSuccess) {
			onLog(syncLogI(strRes.get(Res.string.sync_log_entity_delete_success, id), projectDef))
			true
		} else {
			val message = result.exceptionOrNull()?.message

			onLog(
				syncLogE(
					strRes.get(
						Res.string.sync_log_entity_delete_failed,
						id,
						message ?: "---"
					), projectDef
				)
			)
			false
		}
	}

	private class HealConflictException(val entityId: Int) : Exception()

	private data class TransferState(
		var maxId: Int,
		var combinedDeletions: Set<Int>,
		var resolvedClientSyncData: ProjectSynchronizationData,
		var serverSyncData: ProjectSynchronizationBegan,
		var newClientIds: List<Int>,
		var dirtyEntities: MutableList<EntityOriginalState>,
	)
}