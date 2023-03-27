package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class ProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectEditorRepository: ProjectEditorRepository,
	private val serverProjectApi: ServerProjectApi,
	private val fileSystem: FileSystem,
	private val json: Json
) : KoinComponent {

	private val idRepository: IdRepository by inject { parametersOf(projectDef) }
	private val sceneSynchronizer: SceneSynchronizer by inject { parametersOf(projectDef) }

	private val userId: Long
		get() = globalSettingsRepository.serverSettings?.userId
			?: throw IllegalStateException("Server settings missing")

	private fun getSyncDataPath(): Path = projectDef.path.toOkioPath() / SYNC_FILE_NAME

	private fun loadSyncData(): SynchronizationData {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path).not()) {
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
			val localIsDirty = clientSyncData.dirty.find { it.id == thisId }
			// If our copy is dirty, or this ID hasn't been seen by the server yet
			if (localIsDirty != null || thisId > serverSyncData.lastId) {
				uploadEntity(thisId, syncId)
			}
			// Otherwise download the server's copy
			else {
				downloadEntry(thisId, syncId)
			}
		}

		val newLastId = idRepository.peekNextId() - 1
		serverProjectApi.setSyncData(projectDef, syncId, newLastId)

		finalizeSync()

		val endSyncResult = serverProjectApi.endProjectSync(userId, projectDef.name, syncId)
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