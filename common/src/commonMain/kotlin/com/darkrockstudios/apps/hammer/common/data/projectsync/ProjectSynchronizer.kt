package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
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

	override val projectScope = ProjectDefScope(projectDef)
	private val idRepository: IdRepository by projectInject()
	private val sceneSynchronizer: SceneSynchronizer by projectInject()

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
		serverSyncData: HasProjectResponse
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

	suspend fun sync() {
		val syncId = serverProjectApi.beginProjectSync(userId, projectDef.name).getOrThrow()

		val clientSyncData = loadSyncData().copy(currentSyncId = syncId)
		saveSyncData(clientSyncData)

		val serverSyncData = serverProjectApi.getProjectLastSync(userId, projectDef.name, syncId).getOrThrow()

		// Resolve ID conflicts
		val maxId = handleIdConflicts(clientSyncData, serverSyncData)

		// Transfer Entities
		for (thisId in 1..maxId) {
			Napier.d("Syncing ID $thisId")

			val localIsDirty = clientSyncData.dirty.find { it.id == thisId }
			// If our copy is dirty, or this ID hasn't been seen by the server yet
			if (localIsDirty != null || thisId > serverSyncData.lastId) {
				Napier.d("Upload ID $thisId")
				uploadEntity(thisId, syncId)
			}
			// Otherwise download the server's copy
			else {
				Napier.d("Download ID $thisId")
				downloadEntry(thisId, syncId)
			}
		}

		val newLastId = idRepository.peekNextId() - 1
		val syncFinishedAt = Clock.System.now()
		serverProjectApi.setSyncData(projectDef, syncId, newLastId, syncFinishedAt)

		finalizeSync()

		val endSyncResult = serverProjectApi.endProjectSync(userId, projectDef.name, syncId)

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
	}

	private suspend fun finalizeSync() {
		sceneSynchronizer.finalizeSync()
	}

	private fun findEntityType(id: Int): EntityType {
		val scene = projectEditorRepository.getSceneItemFromId(id)
		return if (scene != null) {
			EntityType.Scene
		} else {
			EntityType.Unknown
		}
	}

	private suspend fun uploadEntity(id: Int, syncId: String) {
		val type = findEntityType(id)
		when (type) {
			EntityType.Scene -> sceneSynchronizer.uploadScene(id, syncId)
			else -> Napier.e("Unknown entity type $type")
		}
	}

	private suspend fun downloadEntry(id: Int, syncId: String) {
		val type = findEntityType(id)
		when (type) {
			EntityType.Scene -> sceneSynchronizer.downloadEntity(id, syncId)
			else -> Napier.e("Unknown entity type $type")
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