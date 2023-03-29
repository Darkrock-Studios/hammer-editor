package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
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

class ProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectEditorRepository: ProjectEditorRepository,
	private val serverProjectApi: ServerProjectApi,
	private val fileSystem: FileSystem,
	private val json: Json
) : ProjectScoped {

	private val defaultDispatcher by injectDefaultDispatcher()

	override val projectScope = ProjectDefScope(projectDef)
	private val idRepository: IdRepository by projectInject()
	private val sceneSynchronizer: SceneSynchronizer by projectInject()

	private val scope = CoroutineScope(defaultDispatcher + SupervisorJob())
	private val conflictResolution = Channel<ApiProjectEntity>()

	init {
		scope.launch {
			for (conflict in conflictResolution) {
				when (conflict) {
					is ApiProjectEntity.SceneEntity -> sceneSynchronizer.conflictResolution.send(conflict)
				}
			}
		}
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
		serverSyncData: ProjectSynchronizationBegan
	): Int {
		var lastId = serverSyncData.lastId
		val newIds = mutableListOf<Int>()
		for (id in clientSyncData.newIds) {
			if (id <= serverSyncData.lastId) {
				val newId = ++lastId
				reIdEntry(id, newId)
				newIds.add(newId)
			}
		}

		val maxId = (newIds + clientSyncData.lastId + serverSyncData.lastId).max()

		// Tell ID Repository to re-find the max ID
		idRepository.findNextId()

		return maxId
	}

	suspend fun sync(
		onProgress: suspend (Float) -> Unit,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	) {
		val syncData = serverProjectApi.beginProjectSync(userId, projectDef.name).getOrThrow()

		onProgress(0.1f)

		val clientSyncData = loadSyncData().copy(currentSyncId = syncData.syncId)
		saveSyncData(clientSyncData)

		onProgress(0.25f)

		// Resolve ID conflicts
		val maxId = handleIdConflicts(clientSyncData, syncData)

		// Transfer Entities
		for (thisId in 1..maxId) {
			Napier.d("Syncing ID $thisId")

			val localIsDirty = clientSyncData.dirty.find { it.id == thisId }
			// If our copy is dirty, or this ID hasn't been seen by the server yet
			if (localIsDirty != null || thisId > syncData.lastId) {
				Napier.d("Upload ID $thisId")
				uploadEntity(thisId, syncData.syncId, onConflict)
			}
			// Otherwise download the server's copy
			else {
				Napier.d("Download ID $thisId")
				downloadEntry(thisId, syncData.syncId)
			}
		}

		onProgress(0.75f)

		finalizeSync()

		onProgress(0.9f)

		val newLastId = idRepository.peekNextId() - 1
		val syncFinishedAt = Clock.System.now()
		val endSyncResult =
			serverProjectApi.endProjectSync(userId, projectDef.name, syncData.syncId, newLastId, syncFinishedAt)

		if (endSyncResult.isFailure) {
			Napier.e("Failed to end sync", endSyncResult.exceptionOrNull())
		} else {
			val finalSyncData = clientSyncData.copy(
				currentSyncId = null,
				lastId = newLastId,
				lastSync = syncFinishedAt
			)
			saveSyncData(finalSyncData)
		}

		onProgress(1f)

		onComplete()
	}

	private suspend fun finalizeSync() {
		sceneSynchronizer.finalizeSync()
	}

	private fun findEntityType(id: Int): EntityType {
		return if (sceneSynchronizer.ownsEntity(id)) {
			EntityType.Scene
		} else {
			EntityType.Unknown
		}
	}

	private suspend fun uploadEntity(id: Int, syncId: String, onConflict: EntityConflictHandler<ApiProjectEntity>) {
		val type = findEntityType(id)
		when (type) {
			EntityType.Scene -> sceneSynchronizer.uploadEntity(id, syncId, onConflict)
			else -> Napier.e("Unknown entity type $type")
		}
	}

	private suspend fun getLocalEntityHash(id: Int): String? {
		val type = findEntityType(id)
		return when (type) {
			EntityType.Scene -> sceneSynchronizer.getEntityHash(id)
			else -> null
		}
	}

	private suspend fun downloadEntry(id: Int, syncId: String) {
		val localEntityHash = getLocalEntityHash(id)
		val entityResponse = serverProjectApi.downloadEntity(
			projectDef = projectDef,
			entityId = id,
			syncId = syncId,
			localHash = localEntityHash
		)

		if (entityResponse.isSuccess) {
			val serverEntity = entityResponse.getOrThrow()
			when (serverEntity.type) {
				ApiProjectEntity.Type.SCENE -> sceneSynchronizer.storeEntity(serverEntity.entity, syncId)
				else -> Napier.e("Unknown entity type ${serverEntity.type}")
			}
		} else {
			if (entityResponse.exceptionOrNull() is EntityNotModifiedException) {
				Napier.d("Entity $id not modified")
			} else {
				Napier.e("Failed to download entity $id", entityResponse.exceptionOrNull())
			}
		}
	}

	private suspend fun reIdEntry(oldId: Int, newId: Int) {
		val type = findEntityType(oldId)
		when (type) {
			EntityType.Scene -> sceneSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			else -> Napier.e("Unknown entity type $type")
		}
	}

	companion object {
		private const val SYNC_FILE_NAME = "sync.json"
	}
}