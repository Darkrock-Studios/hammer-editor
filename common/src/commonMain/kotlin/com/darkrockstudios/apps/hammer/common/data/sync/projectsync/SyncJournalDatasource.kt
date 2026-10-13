package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/**
 * Reads and writes a project's `sync.json`, the file behind [SyncJournal]. It never creates the
 * file on its own: a missing or corrupt journal reads as null and [SyncJournal] rebuilds it.
 */
class SyncJournalDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
	private val json: Json,
) {
	// Corrupt sync data recovers by being rebuilt; not an error to surface.
	@Suppress("SwallowedException")
	suspend fun loadSyncDataOrNull(): ProjectSynchronizationData? {
		val path = getSyncDataPath()
		if (!fileSystem.exists(path)) return null

		return fileSystem.read(path) {
			try {
				json.decodeFromString(readUtf8())
			} catch (e: SerializationException) {
				null
			}
		}
	}

	private fun getSyncDataPath(): Path = projectDef.path.toOkioPath() / SYNC_FILE_NAME

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
 * No-op when the journal doesn't exist. A corrupt journal is deleted so [SyncJournal] rebuilds
 * it from local state.
 */
// Corrupt sync data recovers by deleting it; not an error to surface.
@Suppress("SwallowedException")
fun clearProjectSyncBaseline(projectDef: ProjectDef, fileSystem: FileSystem, json: Json) {
	val path = projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
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

/**
 * Flags a project just restored from a backup for a full conflict check on its next sync (see
 * [ProjectSynchronizationData.verifyAllEntities]). The confirmed hashes and dirty baselines are
 * kept: they are what makes the server raise a conflict when it changed since the backup, whereas
 * a null baseline would let the restored copy overwrite it unchecked. The cached project hash is
 * dropped so the pre-sync probe cannot skip the project.
 *
 * No-op when the journal doesn't exist. A corrupt journal is deleted, as in [clearProjectSyncBaseline].
 */
// Corrupt sync data recovers by deleting it; not an error to surface.
@Suppress("SwallowedException")
fun markProjectRestored(projectDef: ProjectDef, fileSystem: FileSystem, json: Json) {
	val path = projectDef.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME
	if (!fileSystem.exists(path)) return

	val current = try {
		fileSystem.read(path) { json.decodeFromString<ProjectSynchronizationData>(readUtf8()) }
	} catch (e: SerializationException) {
		fileSystem.delete(path)
		return
	}

	val marked = current.copy(
		currentSyncId = null,
		cachedProjectHash = null,
		verifyAllEntities = true,
	)
	if (marked == current) return

	fileSystem.write(path) { writeUtf8(json.encodeToString(marked)) }
}

/**
 * The entities a project has written but not yet had accepted by the server: new ones plus changed
 * ones. Read without a project scope, because callers that only need the number must not pay for
 * opening the whole project.
 *
 * A project with no journal has never synced and so has nothing outstanding. A journal that cannot
 * be read throws rather than reporting zero: callers use this to decide whether local writing is
 * safe to discard, and an unreadable journal is not evidence that it is.
 */
fun loadPendingEntityCount(projectDef: ProjectDef, fileSystem: FileSystem, json: Json): Int {
	val path = projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
	if (!fileSystem.exists(path)) return 0

	val data = fileSystem.read(path) { json.decodeFromString<ProjectSynchronizationData>(readUtf8()) }
	return (data.dirty.map { it.id }.toSet() + data.newIds).size
}
