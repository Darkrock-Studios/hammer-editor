package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.*
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

class ClientProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val serverProjectApi: ServerProjectApi,
	private val fileSystem: FileSystem,
	private val json: Json
) : ProjectScoped {

	private val defaultDispatcher by injectDefaultDispatcher()

	override val projectScope = ProjectDefScope(projectDef)
	private val idRepository: IdRepository by projectInject()
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

	init {
		scope.launch {
			for (conflict in conflictResolution) {
				when (conflict) {
					is ApiProjectEntity.SceneEntity -> sceneSynchronizer.conflictResolution.send(conflict)
					is ApiProjectEntity.NoteEntity -> noteSynchronizer.conflictResolution.send(conflict)
					is ApiProjectEntity.TimelineEventEntity -> timelineSynchronizer.conflictResolution.send(conflict)
					is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.conflictResolution.send(
						conflict
					)

					is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.conflictResolution.send(conflict)
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

	fun shouldAutoSync(): Boolean = globalSettingsRepository.globalSettings.automaticSyncing

	suspend fun isEntityDirty(id: Int): Boolean {
		val syncData = loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	suspend fun markEntityAsDirty(id: Int, oldHash: String) {
		val syncData = loadSyncData()
		val newSyncData = syncData.copy(
			dirty = syncData.dirty + EntityState(id, oldHash)
		)
		saveSyncData(newSyncData)
	}

	fun resolveConflict(entity: ApiProjectEntity) {
		scope.launch {
			conflictResolution.send(entity)
		}
	}

	private val userId: Long
		get() = globalSettingsRepository.serverSettings?.userId
			?: throw IllegalStateException("Server settings missing")

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

	private suspend fun loadSyncData(): ProjectSynchronizationData {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val syncDataJson = readUtf8()
				json.decodeFromString(syncDataJson)
			}
		} else {
			val newData = createSyncData()
			saveSyncData(newData)
			newData
		}
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
		onLog: suspend (String?) -> Unit
	): ProjectSynchronizationData {

		return if (serverSyncData.lastId > clientSyncData.lastId) {
			if (clientSyncData.newIds.isNotEmpty()) {
				var serverLastId = serverSyncData.lastId
				val updatedNewIds = clientSyncData.newIds.toMutableList()
				val updatedDirty = clientSyncData.dirty.toMutableList()

				val localDeletedIds = clientSyncData.deletedIds.toMutableSet()

				for ((ii, id) in clientSyncData.newIds.withIndex()) {
					if (id <= serverSyncData.lastId) {
						onLog("ID $id already exists on server, re-assigning")
						val newId = ++serverLastId

						// Re-ID this currently local only Entity
						reIdEntry(id, newId)
						updatedNewIds[ii] = newId

						// If we have a dirty record for this ID, update it
						val dirtyIndex = clientSyncData.dirty.indexOfFirst { it.id == id }
						if (dirtyIndex > -1) {
							updatedDirty[dirtyIndex] = clientSyncData.dirty[dirtyIndex].copy(id = newId)
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

	suspend fun sync(
		onProgress: suspend (Float, String?) -> Unit,
		onLog: suspend (String?) -> Unit,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	): Boolean {
		return try {
			prepareForSync()

			val serverSyncData = serverProjectApi.beginProjectSync(userId, projectDef.name).getOrThrow()

			onProgress(0.1f, "Server data received")

			val clientSyncData = loadSyncData().copy(currentSyncId = serverSyncData.syncId)
			saveSyncData(clientSyncData)

			val combinedDeletions = serverSyncData.deletedIds + clientSyncData.deletedIds
			val serverDeletedIds =
				serverSyncData.deletedIds.filter { clientSyncData.deletedIds.contains(it).not() }.toSet()
			val newlyDeletedIds =
				clientSyncData.deletedIds.filter { serverSyncData.deletedIds.contains(it).not() }.toSet()

			onProgress(0.2f, "Client data loaded")

			// Resolve ID conflicts
			val resolvedClientSyncData = handleIdConflicts(clientSyncData, serverSyncData, onLog)
			val maxId = (resolvedClientSyncData.newIds + resolvedClientSyncData.lastId + serverSyncData.lastId).max()
			val newClientIds = resolvedClientSyncData.newIds

			val ENTITY_START = 0.3f
			val ENTITY_TOTAL = 0.5f

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
			val failedDeletes = newlyDeletedIds.filter { successfullyDeletedIds.contains(it).not() }.toSet()

			onProgress(ENTITY_START, null)

			var allSuccess = true
			// Transfer Entities
			for (thisId in 1..maxId) {
				if (thisId in combinedDeletions) {
					Napier.d("Skipping deleted ID $thisId")
					continue
				}
				Napier.d("Syncing ID $thisId")

				val localIsDirty = resolvedClientSyncData.dirty.find { it.id == thisId }
				val isNewlyCreated = newClientIds.contains(thisId)
				// If our copy is dirty, or this ID hasn't been seen by the server yet
				if (isNewlyCreated || (localIsDirty != null || thisId > serverSyncData.lastId)) {
					Napier.d("Upload ID $thisId")
					val originalHash = localIsDirty?.originalHash
					allSuccess =
						allSuccess && uploadEntity(thisId, serverSyncData.syncId, originalHash, onConflict, onLog)
				}
				// Otherwise download the server's copy
				else {
					Napier.d("Download ID $thisId")
					allSuccess = allSuccess && downloadEntry(thisId, serverSyncData.syncId, onLog)
				}
				onProgress(ENTITY_START + (ENTITY_TOTAL * (thisId / maxId.toFloat())), null)

				yield()
			}

			val ENTITY_END = ENTITY_START + ENTITY_TOTAL
			onProgress(ENTITY_END, "Entities transferred")

			finalizeSync()

			yield()

			onProgress(0.9f, "Sync finalized")

			val newLastId: Int?
			val syncFinishedAt: Instant?
			// If we failed, send up nulls
			if (allSuccess) {
				newLastId = maxId
				syncFinishedAt = Clock.System.now()
			} else {
				newLastId = null
				syncFinishedAt = null
			}

			val endSyncResult = serverProjectApi.endProjectSync(
				userId,
				projectDef.name,
				serverSyncData.syncId,
				newLastId,
				syncFinishedAt,
			)

			yield()

			if (endSyncResult.isFailure) {
				Napier.e("Failed to end sync", endSyncResult.exceptionOrNull())
				allSuccess = false
			} else {
				if (allSuccess) {
					onLog("Sync data saved")

					if (newLastId != null && syncFinishedAt != null) {
						val finalSyncData = clientSyncData.copy(
							currentSyncId = null,
							lastId = newLastId,
							lastSync = syncFinishedAt,
							dirty = emptyList(),
							newIds = emptyList(),
							deletedIds = combinedDeletions
						)
						saveSyncData(finalSyncData)
					} else {
						onLog("Sync data not saved due to errors")
					}
				} else {
					onLog("Sync data not saved due to errors")
				}
			}

			onProgress(1f, null)

			yield()

			onComplete()

			allSuccess
		} catch (e: Exception) {
			onLog("Sync failed: ${e.message}")
			endSync()
			onComplete()

			if (e is CancellationException) throw e

			false
		}
	}

	private suspend fun endSync() {
		try {
			val syncId = loadSyncData().currentSyncId ?: throw IllegalStateException("No sync ID")

			val endSyncResult = serverProjectApi.endProjectSync(
				userId,
				projectDef.name,
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
		// Create the sync data if it doesnt exist yet
		val path = getSyncDataPath()
		if (!fileSystem.exists(path)) {
			val newData = createSyncData()
			saveSyncData(newData)
		}

		entitySynchronizers.forEach { it.prepareForSync() }
	}

	private suspend fun finalizeSync() {
		entitySynchronizers.forEach { it.finalizeSync() }
	}

	private suspend fun findEntityType(id: Int): EntityType? {
		for (synchronizer in entitySynchronizers) {
			if (synchronizer.ownsEntity(id)) {
				return synchronizer.getEntityType()
			}
		}
		return null
	}

	private suspend fun deleteEntityLocal(id: Int, onLog: suspend (String?) -> Unit) {
		for (synchronizer in entitySynchronizers) {
			if (synchronizer.ownsEntity(id)) {
				synchronizer.deleteEntityLocal(id, onLog)
				return
			}
		}
	}

	private suspend fun deleteEntityRemote(id: Int, syncId: String, onLog: suspend (String?) -> Unit): Boolean {
		val result = serverProjectApi.deleteId(projectDef.name, id, syncId)
		return if (result.isSuccess) {
			onLog("Deleted ID $id on server")
			true
		} else {
			val message = result.exceptionOrNull()?.message
			onLog("Failed to delete ID $id on server: $message")
			false
		}
	}


	private suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onLog: suspend (String?) -> Unit
	): Boolean {
		val type = findEntityType(id)
		if (type != null) {
			return when (type) {
				EntityType.Scene -> sceneSynchronizer.uploadEntity(id, syncId, originalHash, onConflict, onLog)
				EntityType.Note -> noteSynchronizer.uploadEntity(id, syncId, originalHash, onConflict, onLog)
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
			onLog("Failed to upload entity $id: unknown type")
			return false
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

	private suspend fun downloadEntry(id: Int, syncId: String, onLog: suspend (String?) -> Unit): Boolean {
		val localEntityHash = getLocalEntityHash(id)
		val entityResponse = serverProjectApi.downloadEntity(
			projectDef = projectDef,
			entityId = id,
			syncId = syncId,
			localHash = localEntityHash
		)

		return if (entityResponse.isSuccess) {
			val serverEntity = entityResponse.getOrThrow().entity
			when (serverEntity) {
				is ApiProjectEntity.SceneEntity -> sceneSynchronizer.storeEntity(serverEntity, syncId, onLog)
				is ApiProjectEntity.NoteEntity -> noteSynchronizer.storeEntity(serverEntity, syncId, onLog)
				is ApiProjectEntity.TimelineEventEntity -> timelineSynchronizer.storeEntity(serverEntity, syncId, onLog)
				is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.storeEntity(
					serverEntity,
					syncId,
					onLog
				)

				is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.storeEntity(serverEntity, syncId, onLog)
			}
			onLog("Entity $id downloaded")
			true
		} else {
			if (entityResponse.exceptionOrNull() is EntityNotModifiedException) {
				onLog("Entity $id not modified")
				true
			} else {
				Napier.e("Failed to download entity $id", entityResponse.exceptionOrNull())
				onLog("Failed to download entity $id")
				false
			}
		}
	}

	private suspend fun reIdEntry(oldId: Int, newId: Int) {
		val type = findEntityType(oldId) ?: throw IllegalArgumentException("Entity $oldId not found for reId")
		when (type) {
			EntityType.Scene -> sceneSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.Note -> noteSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.TimelineEvent -> timelineSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.reIdEntity(oldId = oldId, newId = newId)
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
	}
}