package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

class SyncDataDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
	private val json: Json,
	private val idAllocator: IdAllocator,
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

	// Corrupt sync data recovers by recreating it; not an error to surface.
	@Suppress("SwallowedException")
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

	/**
	 * Clears the cached project-wide hash so the next pre-sync probe can't skip a project whose
	 * content just changed. No-op when sync data doesn't exist yet or the hash is already cleared,
	 * so it never creates a journal for a not-yet-synchronized project.
	 */
	suspend fun invalidateProjectHash() {
		val current = loadSyncDataOrNull() ?: return
		if (current.cachedProjectHash == null) return
		saveSyncData(current.copy(cachedProjectHash = null))
	}

	suspend fun createSyncData(): ProjectSynchronizationData {
		val lastId = idAllocator.peekLastId()

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
		const val SYNC_FILE_NAME = "sync.json"
	}
}

/**
 * Resets everything in the entity journal that records agreement with a *specific* server, for
 * scope-less callers moving a project to a different server project. Local pending work the new
 * server has never seen — [ProjectSynchronizationData.dirty], `newIds`, `deletedIds`, `lastId` —
 * is kept, so an unsynced edit or a tombstone can't be lost by the move. Dirty entries keep their
 * id but drop [EntityOriginalState.originalHash]: a baseline the old server confirmed would forge
 * a phantom conflict against the new one, while a null baseline uploads unconditionally.
 *
 * No-op when the journal doesn't exist. A corrupt journal is deleted so
 * [SyncDataDatasource.createSyncData] rebuilds it from local state, matching [loadSyncData].
 */
// Corrupt sync data recovers by deleting it; not an error to surface.
@Suppress("SwallowedException")
fun clearProjectSyncBaseline(projectDef: ProjectDef, fileSystem: FileSystem, json: Json) {
	val path = projectDef.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME
	if (!fileSystem.exists(path)) return

	val current = try {
		fileSystem.read(path) { json.decodeFromString<ProjectSynchronizationData>(readUtf8()) }
	} catch (e: SerializationException) {
		fileSystem.delete(path)
		return
	}

	val cleared = current.copy(
		currentSyncId = null,
		lastSync = Instant.DISTANT_PAST,
		dirty = current.dirty.map { it.copy(originalHash = null) },
		syncedHashes = emptyMap(),
		cachedProjectHash = null,
		hashAlgoVersion = 0,
	)
	if (cleared == current) return

	fileSystem.write(path) { writeUtf8(json.encodeToString(cleared)) }
}
