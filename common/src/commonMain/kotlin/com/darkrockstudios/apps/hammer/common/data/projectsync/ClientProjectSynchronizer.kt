package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.loadProjectId
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.EntityNotFoundException
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.component.inject

class ClientProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val serverProjectApi: ServerProjectApi,
	private val fileSystem: FileSystem,
	private val json: Json
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val defaultDispatcher by injectDefaultDispatcher()
	private val idRepository: IdRepository by projectInject()
	private val backupRepository: ProjectBackupRepository by inject()
	private val networkConnectivity: NetworkConnectivity by inject()
	private val strRes: StrRes by inject()
	private val clock: Clock by inject()
	private val projectMetadataDatasource: ProjectMetadataDatasource by inject()

	private val sceneSynchronizer: ClientSceneSynchronizer by projectInject()
	private val noteSynchronizer: ClientNoteSynchronizer by projectInject()
	private val timelineSynchronizer: ClientTimelineSynchronizer by projectInject()
	private val encyclopediaSynchronizer: ClientEncyclopediaSynchronizer by projectInject()
	private val sceneDraftSynchronizer: ClientSceneDraftSynchronizer by projectInject()
	private val entitySynchronizers by lazy {
		listOf(
			sceneSynchronizer,
			noteSynchronizer,
			timelineSynchronizer,
			encyclopediaSynchronizer,
			sceneDraftSynchronizer
		)
	}

	private val scope = CoroutineScope(defaultDispatcher + SupervisorJob())
	private val conflictResolution = Channel<ApiProjectEntity>()

	val syncCompleteEvent = Channel<Boolean>()

	init {
		scope.launch {
			for (conflict in conflictResolution) {
				when (conflict) {
					is ApiProjectEntity.SceneEntity ->
						sceneSynchronizer.conflictResolution.send(conflict)

					is ApiProjectEntity.NoteEntity ->
						noteSynchronizer.conflictResolution.send(conflict)

					is ApiProjectEntity.TimelineEventEntity ->
						timelineSynchronizer.conflictResolution.send(conflict)

					is ApiProjectEntity.EncyclopediaEntryEntity ->
						encyclopediaSynchronizer.conflictResolution.send(conflict)

					is ApiProjectEntity.SceneDraftEntity ->
						sceneDraftSynchronizer.conflictResolution.send(conflict)
				}
			}
		}
	}

	fun isServerSynchronized(): Boolean {
		return globalSettingsRepository.serverSettings != null
	}

	suspend fun needsSync(): Boolean {
		return loadSyncData().dirty.isNotEmpty()
	}

	suspend fun deletedIds(): Set<Int> {
		return loadSyncDataOrNull()?.deletedIds ?: emptySet()
	}

	suspend fun shouldAutoSync(): Boolean = globalSettingsRepository.serverIsSetup() &&
		globalSettingsRepository.globalSettings.automaticSyncing &&
		networkConnectivity.hasActiveConnection() &&
		needsSync()

	suspend fun isEntityDirty(id: Int): Boolean {
		val syncData = loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	suspend fun markEntityAsDirty(id: Int, oldHash: String) {
		val syncData = loadSyncData()
		val newSyncData = syncData.copy(
			dirty = syncData.dirty + EntityOriginalState(id, oldHash)
		)
		saveSyncData(newSyncData)
	}

	fun resolveConflict(entity: ApiProjectEntity) {
		scope.launch {
			conflictResolution.send(entity)
		}
	}

	private suspend fun userId(): Long {
		return globalSettingsRepository.serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}

	private fun getSyncDataPath(): Path = projectDef.path.toOkioPath() / SYNC_FILE_NAME

	private suspend fun createSyncData(): ProjectSynchronizationData {
		val lastId = idRepository.peekNextId() - 1

		val missingIds = mutableSetOf<Int>()
		for (id in 1..lastId) {
			val entityType = findEntityType(id)
			if (entityType == null) {
				missingIds.add(id)
			}
		}

		val newData = ProjectSynchronizationData(
			lastId = lastId,
			newIds = emptyList(),
			lastSync = Instant.DISTANT_PAST,
			dirty = emptyList(),
			deletedIds = missingIds
		)

		return newData
	}

	private suspend fun loadSyncDataOrNull(): ProjectSynchronizationData? {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
			loadSyncData()
		} else {
			null
		}
	}

	private suspend fun loadSyncData(): ProjectSynchronizationData {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val syncDataJson = readUtf8()
				try {
					json.decodeFromString(syncDataJson)
				} catch (e: SerializationException) {
					createAndSaveSyncData()
				}
			}
		} else {
			createAndSaveSyncData()
		}
	}

	private suspend fun createAndSaveSyncData(): ProjectSynchronizationData {
		val newData = createSyncData()
		saveSyncData(newData)
		return newData
	}

	private fun saveSyncData(data: ProjectSynchronizationData) {
		val path = getSyncDataPath()
		fileSystem.write(path) {
			val syncDataJson = json.encodeToString(data)
			writeUtf8(syncDataJson)
		}
	}

	private suspend fun handleIdConflicts(
		clientSyncData: ProjectSynchronizationData,
		serverSyncData: ProjectSynchronizationBegan,
		onLog: OnSyncLog
	): ProjectSynchronizationData {

		return if (serverSyncData.lastId > clientSyncData.lastId) {
			if (clientSyncData.newIds.isNotEmpty()) {
				var serverLastId = serverSyncData.lastId
				val updatedNewIds = clientSyncData.newIds.toMutableList()
				val updatedDirty = clientSyncData.dirty.toMutableList()

				val localDeletedIds = clientSyncData.deletedIds.toMutableSet()

				for ((ii, id) in clientSyncData.newIds.withIndex()) {
					if (id <= serverSyncData.lastId) {
						onLog(
							syncLogI(
								"ID $id already exists on server, re-assigning",
								projectDef.name
							)
						)
						val newId = ++serverLastId

						// Re-ID this currently local only Entity
						reIdEntry(id, newId)
						updatedNewIds[ii] = newId

						// If we have a dirty record for this ID, update it
						val dirtyIndex = clientSyncData.dirty.indexOfFirst { it.id == id }
						if (dirtyIndex > -1) {
							updatedDirty[dirtyIndex] =
								clientSyncData.dirty[dirtyIndex].copy(id = newId)
						}

						// If this is a locally deleted ID, update it
						if (localDeletedIds.contains(id)) {
							localDeletedIds.remove(id)
							localDeletedIds.add(newId)
						}
					}
				}

				// Tell ID Repository to re-find the max ID
				idRepository.findNextId()

				clientSyncData.copy(
					newIds = updatedNewIds,
					lastId = updatedNewIds.max(),
					dirty = updatedDirty,
					deletedIds = localDeletedIds
				)
			} else {
				clientSyncData
			}
		} else {
			clientSyncData
		}
	}

	private suspend fun getEntityState(clientSyncData: ProjectSynchronizationData): ClientEntityState {
		val entities = entitySynchronizers.flatMap { syncher ->
			syncher.hashEntities(clientSyncData.newIds)
		}.toSet()

		return ClientEntityState(entities)
	}

	suspend fun sync(
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
		onlyNew: Boolean = false,
	): Boolean {
		var allSuccess = false
		return try {
			prepareForSync()

			val metadata = projectMetadataDatasource.loadMetadata(projectDef)
			val serverProjectId = metadata.info.serverProjectId
				?: error("Server project ID missing for: ${projectDef.name}")

			var clientSyncData = loadSyncData()
			val entityState = if (onlyNew) {
				null
			} else {
				getEntityState(clientSyncData)
			}

			yield()

			onProgress(
				0.05f,
				syncLogI(strRes.get(MR.strings.sync_log_client_data_computed), projectDef)
			)

			val serverSyncData =
				serverProjectApi.beginProjectSync(
					userId(),
					projectDef.name,
					serverProjectId,
					entityState,
					onlyNew
				).getOrThrow()

			onProgress(
				0.1f,
				syncLogI(strRes.get(MR.strings.sync_log_server_data_loaded), projectDef)
			)

			clientSyncData = clientSyncData.copy(currentSyncId = serverSyncData.syncId)
			saveSyncData(clientSyncData)

			val combinedDeletions = serverSyncData.deletedIds + clientSyncData.deletedIds
			val serverDeletedIds =
				serverSyncData.deletedIds.filter { clientSyncData.deletedIds.contains(it).not() }
					.toSet()
			val newlyDeletedIds =
				clientSyncData.deletedIds.filter { serverSyncData.deletedIds.contains(it).not() }
					.toSet()
			val dirtyEntities = clientSyncData.dirty.toMutableList()

			onProgress(
				0.2f,
				syncLogI(strRes.get(MR.strings.sync_log_client_data_loaded), projectDef)
			)

			yield()

			if (
				globalSettingsRepository.globalSettings.automaticBackups &&
				backupRepository.supportsBackup()
			) {
				val backupDef = backupRepository.createBackup(projectDef)

				if (backupDef != null) {
					onProgress(
						0.25f,
						syncLogI(
							strRes.get(MR.strings.sync_log_backup_made, backupDef.path.name),
							projectDef
						)
					)
				} else {
					throw IllegalStateException("Failed to make local backup")
				}

				yield()
			}

			// Resolve ID conflicts
			val resolvedClientSyncData = handleIdConflicts(clientSyncData, serverSyncData, onLog)
			val currentMaxId: Int = idRepository.let {
				it.findNextId()
				it.peekLastId()
			}
			val maxId: Int = (resolvedClientSyncData.newIds +
				listOf(currentMaxId, serverSyncData.lastId, resolvedClientSyncData.lastId)
				).max()
			val newClientIds = resolvedClientSyncData.newIds

			// Handle IDs newly deleted on server
			for (id in serverDeletedIds) {
				deleteEntityLocal(id, onLog)
				yield()
			}

			val successfullyDeletedIds = mutableSetOf<Int>()
			// Handle IDs newly deleted on client
			for (id in newlyDeletedIds) {
				if (deleteEntityRemote(id, serverSyncData.syncId, onLog)) {
					successfullyDeletedIds.add(id)
				}
			}
			val failedDeletes =
				newlyDeletedIds.filter { successfullyDeletedIds.contains(it).not() }.toSet()
			if (failedDeletes.isNotEmpty()) {
				Napier.d("Failed to Delete IDs: ${failedDeletes.joinToString(",")}")
			}

			// Remove any dirty that were deleted
			combinedDeletions.forEach { deletedId ->
				dirtyEntities.find { it.id == deletedId }?.let { deleted ->
					dirtyEntities.remove(deleted)
				}
			}

			onProgress(ENTITY_START, null)

			val state = TransferState(
				maxId = maxId,
				combinedDeletions = combinedDeletions,
				resolvedClientSyncData = clientSyncData,
				serverSyncData = serverSyncData,
				newClientIds = newClientIds,
				dirtyEntities = dirtyEntities
			)

			// Transfer Entities
			allSuccess = if (onlyNew) {
				uploadNewEntities(
					newClientIds,
					serverSyncData,
					dirtyEntities,
					onProgress,
					onLog
				)
			} else {
				fullEntityTransfer(
					state,
					onProgress,
					onLog,
					onConflict
				)
			}

			onProgress(
				ENTITY_END,
				syncLogI(strRes.get(MR.strings.sync_log_entities_transferred), projectDef)
			)

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
				serverProjectId,
				serverSyncData.syncId,
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
					dirtyEntities.clear()

					if (newLastId != null && syncFinishedAt != null) {
						val finalSyncData = clientSyncData.copy(
							currentSyncId = null,
							lastId = newLastId,
							lastSync = syncFinishedAt,
							dirty = state.dirtyEntities,
							newIds = emptyList(),
							deletedIds = state.combinedDeletions
						)
						saveSyncData(finalSyncData)
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

			allSuccess
		} catch (e: Exception) {
			Napier.e("Sync failed: ${e.message}", e)
			onLog(
				syncLogE(
					strRes.get(MR.strings.sync_log_entity_failed, e.message ?: "---"),
					projectDef
				)
			)
			endSync()
			onComplete()

			if (e is CancellationException) throw e

			false
		} finally {
			syncCompleteEvent.trySend(allSuccess)
		}
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
			val message = strRes.get(MR.strings.sync_log_entity_conflict, entity.id, entity.type)
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
			val clientHasEntity = clientHasEntity(thisId)

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
							val entityExistsLocally = (findEntityType(entityId) != null)
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

	private data class TransferState(
		var maxId: Int,
		var combinedDeletions: Set<Int>,
		var resolvedClientSyncData: ProjectSynchronizationData,
		var serverSyncData: ProjectSynchronizationBegan,
		var newClientIds: List<Int>,
		var dirtyEntities: MutableList<EntityOriginalState>,
	)

	private suspend fun endSync() {
		try {
			val syncId = loadSyncData().currentSyncId ?: throw IllegalStateException("No sync ID")
			val serverProjectId = projectMetadataDatasource.loadProjectId(projectDef)

			val endSyncResult = serverProjectApi.endProjectSync(
				userId(),
				projectDef.name,
				serverProjectId,
				syncId,
				null,
				null
			)

			if (endSyncResult.isFailure) {
				Napier.e("Failed to end sync", endSyncResult.exceptionOrNull())
			} else {
				val finalSyncData = loadSyncData().copy(currentSyncId = null)
				saveSyncData(finalSyncData)
			}
		} catch (e: IOException) {
			Napier.e("Sync failed", e)
		} catch (e: IllegalStateException) {
			Napier.e("Sync failed", e)
		}
	}

	private suspend fun prepareForSync() {
		idRepository.findNextId()

		entitySynchronizers.forEach { it.prepareForSync() }

		// Create the sync data if it doesnt exist yet
		val path = getSyncDataPath()
		if (!fileSystem.exists(path)) {
			val newData = createSyncData()
			saveSyncData(newData)
		}
	}

	private suspend fun finalizeSync() {
		entitySynchronizers.forEach { it.finalizeSync() }
	}

	private suspend fun clientHasEntity(id: Int): Boolean {
		return findEntityType(id) != null
	}

	private suspend fun findEntityType(id: Int): EntityType? {
		for (synchronizer in entitySynchronizers) {
			if (synchronizer.ownsEntity(id)) {
				return synchronizer.getEntityType()
			}
		}
		return null
	}

	private suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		for (synchronizer in entitySynchronizers) {
			if (synchronizer.ownsEntity(id)) {
				synchronizer.deleteEntityLocal(id, onLog)
				return
			}
		}
	}

	private suspend fun deleteEntityRemote(id: Int, syncId: String, onLog: OnSyncLog): Boolean {
		val projectId = projectMetadataDatasource.loadProjectId(projectDef)
		val result = serverProjectApi.deleteId(projectDef.name, projectId, id, syncId)
		return if (result.isSuccess) {
			onLog(syncLogI(strRes.get(MR.strings.sync_log_entity_delete_success, id), projectDef))
			true
		} else {
			val message = result.exceptionOrNull()?.message

			onLog(
				syncLogE(
					strRes.get(
						MR.strings.sync_log_entity_delete_failed,
						id,
						message ?: "---"
					), projectDef
				)
			)
			false
		}
	}

	private suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onLog: OnSyncLog
	): Boolean {
		val type = findEntityType(id)
		if (type != null) {
			return when (type) {
				EntityType.Scene -> sceneSynchronizer.uploadEntity(
					id,
					syncId,
					originalHash,
					onConflict,
					onLog
				)

				EntityType.Note -> noteSynchronizer.uploadEntity(
					id,
					syncId,
					originalHash,
					onConflict,
					onLog
				)

				EntityType.TimelineEvent -> timelineSynchronizer.uploadEntity(
					id,
					syncId,
					originalHash,
					onConflict,
					onLog
				)

				EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.uploadEntity(
					id,
					syncId,
					originalHash,
					onConflict,
					onLog
				)

				EntityType.SceneDraft -> sceneDraftSynchronizer.uploadEntity(
					id,
					syncId,
					originalHash,
					onConflict,
					onLog
				)
			}
		} else {
			onLog(
				syncLogW(
					strRes.get(MR.strings.sync_log_entity_upload_entity_not_owned, id),
					projectDef
				)
			)
			return true
		}
	}

	private suspend fun getLocalEntityHash(id: Int): String? {
		val type = findEntityType(id)
		return if (type == null) {
			null
		} else {
			when (type) {
				EntityType.Scene -> sceneSynchronizer.getEntityHash(id)
				EntityType.Note -> noteSynchronizer.getEntityHash(id)
				EntityType.TimelineEvent -> timelineSynchronizer.getEntityHash(id)
				EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.getEntityHash(id)
				EntityType.SceneDraft -> sceneDraftSynchronizer.getEntityHash(id)
			}
		}
	}

	private suspend fun downloadEntry(
		id: Int,
		syncId: String,
		onLog: OnSyncLog
	): CResult<Unit> {
		val localEntityHash = getLocalEntityHash(id)
		val serverProjectId = projectMetadataDatasource.loadProjectId(projectDef)
		val entityResponse = serverProjectApi.downloadEntity(
			projectName = projectDef.name,
			projectId = serverProjectId,
			entityId = id,
			syncId = syncId,
			localHash = localEntityHash
		)

		return if (entityResponse.isSuccess) {
			val serverEntity = entityResponse.getOrThrow().entity
			val success = when (serverEntity) {
				is ApiProjectEntity.SceneEntity -> sceneSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)

				is ApiProjectEntity.NoteEntity -> noteSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)

				is ApiProjectEntity.TimelineEventEntity -> timelineSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)

				is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)

				is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)
			}

			if (success) {
				onLog(
					syncLogI(
						strRes.get(MR.strings.sync_log_entity_download_success, id),
						projectDef
					)
				)
			} else {
				onLog(
					syncLogE(
						strRes.get(MR.strings.sync_log_entity_download_failed_general, id),
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
							strRes.get(MR.strings.sync_log_entity_download_not_modified, id),
							projectDef
						)
					)
					CResult.success()
				}

				is EntityNotFoundException -> {
					onLog(
						syncLogW(
							strRes.get(
								MR.strings.sync_log_entity_download_failed_not_found,
								id
							), projectDef
						)
					)
					CResult.failure(EntityNotFoundException(id))
				}

				else -> {
					val message = strRes.get(MR.strings.sync_log_entity_download_failed_general, id)
					Napier.e(message, entityResponse.exceptionOrNull())
					onLog(syncLogE(message, projectDef))
					CResult.failure(
						entityResponse.exceptionOrNull() ?: IllegalStateException("Unknown error")
					)
				}
			}
		}
	}

	private suspend fun reIdEntry(oldId: Int, newId: Int) {
		val type = findEntityType(oldId)
			?: throw IllegalArgumentException("Entity $oldId not found for reId")
		when (type) {
			EntityType.Scene -> sceneSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.Note -> noteSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.TimelineEvent -> timelineSynchronizer.reIdEntity(
				oldId = oldId,
				newId = newId
			)

			EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.reIdEntity(
				oldId = oldId,
				newId = newId
			)

			EntityType.SceneDraft -> sceneDraftSynchronizer.reIdEntity(oldId = oldId, newId = newId)
		}
	}

	suspend fun recordNewId(claimedId: Int) {
		if (isServerSynchronized().not()) return

		val syncData = loadSyncData()
		val newSyncData = syncData.copy(newIds = syncData.newIds + claimedId)
		saveSyncData(newSyncData)
	}

	suspend fun recordIdDeletion(deletedId: Int) {
		if (isServerSynchronized().not()) return

		val syncData = loadSyncData()
		val updated = syncData.deletedIds + deletedId
		val newSyncData = syncData.copy(deletedIds = updated)
		saveSyncData(newSyncData)
	}

	companion object {
		private const val SYNC_FILE_NAME = "sync.json"
		private const val ENTITY_START = 0.3f
		private const val ENTITY_TOTAL = 0.5f
		private const val ENTITY_END = ENTITY_START + ENTITY_TOTAL
	}
}
