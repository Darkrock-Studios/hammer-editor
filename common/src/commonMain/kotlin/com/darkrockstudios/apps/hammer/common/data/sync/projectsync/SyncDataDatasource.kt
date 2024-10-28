package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class SyncDataDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
	private val json: Json,
	private val idRepository: IdRepository,
	private val entitySynchronizers: EntitySynchronizers
) {
	suspend fun loadSyncDataOrNull(): ProjectSynchronizationData? {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
			loadSyncData()
		} else {
			null
		}
	}

	suspend fun loadSyncData(): ProjectSynchronizationData {
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

	private fun getSyncDataPath(): Path = projectDef.path.toOkioPath() / SYNC_FILE_NAME

	private suspend fun createAndSaveSyncData(): ProjectSynchronizationData {
		val newData = createSyncData()
		saveSyncData(newData)
		return newData
	}

	fun saveSyncData(data: ProjectSynchronizationData) {
		val path = getSyncDataPath()
		fileSystem.write(path) {
			val syncDataJson = json.encodeToString(data)
			writeUtf8(syncDataJson)
		}
	}

	suspend fun createSyncData(): ProjectSynchronizationData {
		val lastId = idRepository.peekNextId() - 1

		val missingIds = mutableSetOf<Int>()
		for (id in 1..lastId) {
			val entityType = entitySynchronizers.findEntityType(id)
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

	fun syncDataExists(): Boolean {
		val path = getSyncDataPath()
		return fileSystem.exists(path)
	}

	companion object {
		private const val SYNC_FILE_NAME = "sync.json"
	}
}