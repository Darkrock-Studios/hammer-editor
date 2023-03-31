package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
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

	private val scope = CoroutineScope(defaultDispatcher + SupervisorJob())
	private val conflictResolution = Channel<ApiProjectEntity>()

	init {
		scope.launch {
			for (conflict in conflictResolution) {
				when (conflict) {
					is ApiProjectEntity.SceneEntity -> sceneSynchronizer.conflictResolution.send(conflict)
					is ApiProjectEntity.NoteEntity -> noteSynchronizer.conflictResolution.send(conflict)
				}
			}
		}
	}

	fun isServerSynchronized(): Boolean {
		return globalSettingsRepository.serverSettings != null
	}

	fun isEntityDirty(id: Int): Boolean {
		val syncData = loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	fun markEntityAsDirty(id: Int, oldHash: String) {
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

	private fun loadSyncData(): SynchronizationData {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val syncDataJson = readUtf8()
				json.decodeFromString(syncDataJson)
			}
		} else {
			val newData = SynchronizationData(
				lastId = 0,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = emptyList()
			)
			saveSyncData(newData)

			newData
		}
	}

	private fun saveSyncData(data: SynchronizationData) {
		val path = getSyncDataPath()
		fileSystem.write(path) {
			val syncDataJson = json.encodeToString(data)
			writeUtf8(syncDataJson)
		}
	}

	private suspend fun handleIdConflicts(
		clientSyncData: SynchronizationData,
		serverSyncData: ProjectSynchronizationBegan,
		onLog: suspend (String?) -> Unit
	): Int {
		var serverLastId = serverSyncData.lastId
		val clientLastId = idRepository.peekNextId() - 1
		for (id in clientSyncData.newIds) {
			if (id <= serverSyncData.lastId) {
				onLog("ID $id already exists on server, re-assigning")
				val newId = ++serverLastId
				reIdEntry(id, newId)
			}
		}

		val maxId = (clientSyncData.newIds + clientSyncData.lastId + serverSyncData.lastId).max()

		// Tell ID Repository to re-find the max ID
		idRepository.findNextId()

		return maxId
	}

	suspend fun sync(
		onProgress: suspend (Float, String?) -> Unit,
		onLog: suspend (String?) -> Unit,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	) {
		prepareForSync()

		val syncData = serverProjectApi.beginProjectSync(userId, projectDef.name).getOrThrow()

		onProgress(0.1f, "Server data received")

		val clientSyncData = loadSyncData().copy(currentSyncId = syncData.syncId)
		val newClientIds = clientSyncData.newIds
		saveSyncData(clientSyncData)

		onProgress(0.25f, "Client data loaded")

		// Resolve ID conflicts
		val maxId = handleIdConflicts(clientSyncData, syncData, onLog)
		val newIdsUnaltered = maxId > syncData.lastId

		onProgress(0.25f, null)

		var allSuccess = true
		// Transfer Entities
		for (thisId in 1..maxId) {
			Napier.d("Syncing ID $thisId")

			val localIsDirty = clientSyncData.dirty.find { it.id == thisId }
			val isNewlyCreated = newClientIds.contains(thisId) && newIdsUnaltered
			// If our copy is dirty, or this ID hasn't been seen by the server yet
			if (isNewlyCreated || localIsDirty != null || thisId > syncData.lastId) {
				Napier.d("Upload ID $thisId")
				val originalHash = localIsDirty?.originalHash
				allSuccess = allSuccess && uploadEntity(thisId, syncData.syncId, originalHash, onConflict, onLog)
			}
			// Otherwise download the server's copy
			else {
				Napier.d("Download ID $thisId")
				allSuccess = allSuccess && downloadEntry(thisId, syncData.syncId, onLog)
			}
		}

		onProgress(0.75f, "Entities transferred")

		finalizeSync()

		onProgress(0.9f, "Sync finalized")

		val newLastId: Int
		val syncFinishedAt: Instant
		// If we failed, send up the old data
		if (allSuccess) {
			newLastId = idRepository.peekNextId() - 1
			syncFinishedAt = Clock.System.now()
		} else {
			newLastId = syncData.lastId
			syncFinishedAt = syncData.lastSync
		}

		val endSyncResult =
			serverProjectApi.endProjectSync(userId, projectDef.name, syncData.syncId, newLastId, syncFinishedAt)

		if (endSyncResult.isFailure) {
			Napier.e("Failed to end sync", endSyncResult.exceptionOrNull())
		} else {
			if (allSuccess) {
				onLog("Sync data saved")

				val finalSyncData = clientSyncData.copy(
					currentSyncId = null,
					lastId = newLastId,
					lastSync = syncFinishedAt,
					dirty = emptyList(),
					newIds = emptyList()
				)
				saveSyncData(finalSyncData)
			} else {
				onLog("Sync data not saved due to errors")
			}
		}

		onProgress(1f, null)

		onComplete()
	}

	private suspend fun prepareForSync() {
		sceneSynchronizer.prepareForSync()
		noteSynchronizer.prepareForSync()
	}

	private suspend fun finalizeSync() {
		sceneSynchronizer.finalizeSync()
		noteSynchronizer.finalizeSync()
	}

	private suspend fun findEntityType(id: Int): EntityType {
		return if (sceneSynchronizer.ownsEntity(id)) {
			EntityType.Scene
		} else if (noteSynchronizer.ownsEntity(id)) {
			EntityType.Note
		} else {
			throw IllegalArgumentException("Unknown entity type for ID $id")
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
		return when (type) {
			EntityType.Scene -> sceneSynchronizer.uploadEntity(id, syncId, originalHash, onConflict, onLog)
			EntityType.Note -> noteSynchronizer.uploadEntity(id, syncId, originalHash, onConflict, onLog)
		}
	}

	private suspend fun getLocalEntityHash(id: Int): String? {
		val type = findEntityType(id)
		return when (type) {
			EntityType.Scene -> sceneSynchronizer.getEntityHash(id)
			EntityType.Note -> noteSynchronizer.getEntityHash(id)
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
		val type = findEntityType(oldId)
		when (type) {
			EntityType.Scene -> sceneSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.Note -> noteSynchronizer.reIdEntity(oldId = oldId, newId = newId)
		}
	}

	fun recordNewId(claimedId: Int) {
		val syncData = loadSyncData()
		val newSyncData = syncData.copy(newIds = syncData.newIds + claimedId)
		saveSyncData(newSyncData)
	}

	companion object {
		private const val SYNC_FILE_NAME = "sync.json"
	}
}