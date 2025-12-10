package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_END
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_START
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer.Companion.ENTITY_TOTAL
import com.darkrockstudios.apps.hammer.common.server.EntityNotFoundException
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.yield

class EntityTransferOperation(
	projectDef: ProjectDef,
	private val strRes: StrRes,
	private val entitySynchronizers: EntitySynchronizers,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val serverProjectApi: ServerProjectApi
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
					val originalHash = localIsDirty?.originalHash
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
								Napier.d("Entity ID $entityId missing from server, but it does exist locally, should we upload it? How did we get here?")
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
			projectName = projectDef.name,
			projectId = serverProjectId,
			entityId = id,
			syncId = syncId,
			localHash = localEntityHash
		)

		return if (entityResponse.isSuccess) {
			val success = when (val serverEntity = entityResponse.getOrThrow().entity) {
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
				onLog(
					syncLogI(
						strRes.get(Res.string.sync_log_entity_download_success, id),
						projectDef
					)
				)
			} else {
				onLog(
					syncLogE(
						strRes.get(Res.string.sync_log_entity_download_failed_general, id),
						projectDef
					)
				)
			}

			CResult.success()
		} else {
			when (entityResponse.exceptionOrNull()) {
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

				else -> {
					val message = strRes.get(Res.string.sync_log_entity_download_failed_general, id)
					Napier.e(message, entityResponse.exceptionOrNull())
					onLog(syncLogE(message, projectDef))
					CResult.failure(
						entityResponse.exceptionOrNull() ?: IllegalStateException("Unknown error")
					)
				}
			}
		}
	}

	private suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onLog: OnSyncLog
	): Boolean {
		val type: EntityType? = entitySynchronizers.findEntityType(id)
		return if (type != null) {
			entitySynchronizers[type].uploadEntity(id, syncId, originalHash, onConflict, onLog)
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
		val result = serverProjectApi.deleteId(projectDef.name, projectId, id, syncId)
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

	private data class TransferState(
		var maxId: Int,
		var combinedDeletions: Set<Int>,
		var resolvedClientSyncData: ProjectSynchronizationData,
		var serverSyncData: ProjectSynchronizationBegan,
		var newClientIds: List<Int>,
		var dirtyEntities: MutableList<EntityOriginalState>,
	)
}