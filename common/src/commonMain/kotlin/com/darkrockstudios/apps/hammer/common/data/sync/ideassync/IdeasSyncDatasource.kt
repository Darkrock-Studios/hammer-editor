package com.darkrockstudios.apps.hammer.common.data.sync.ideassync

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.koin.core.component.KoinComponent

/**
 * Reads and writes the `.ideas/sync.json` sidecar. The file is created by the first ideas sync;
 * its existence is what marks this install as "has synced ideas before" — deletes on a
 * never-synced install are not recorded (there is no server copy to resurrect from), mirroring
 * how the entity journal only records ids once syncing has begun.
 */
class IdeasSyncDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val ideasDatasource: IdeasDatasource,
) : KoinComponent {
	private val ioDispatcher = injectIoDispatcherNow()

	fun getSyncDataPath(): HPath =
		(ideasDatasource.getIdeasDirectory().toOkioPath() / SYNC_FILE_NAME).toHPath()

	suspend fun hasSynced(): Boolean = withContext(ioDispatcher) {
		fileSystem.exists(getSyncDataPath().toOkioPath())
	}

	// Corrupt sync data recovers as empty bookkeeping (baseline-less uploads, backfill on next
	// sync); not an error to surface.
	@Suppress("SwallowedException")
	suspend fun load(): IdeasSynchronizationData = withContext(ioDispatcher) {
		val path = getSyncDataPath().toOkioPath()
		if (!fileSystem.exists(path)) return@withContext IdeasSynchronizationData()
		try {
			fileSystem.read(path) { json.decodeFromString<IdeasSynchronizationData>(readUtf8()) }
		} catch (e: SerializationException) {
			Napier.w("Corrupt ideas sync.json; starting fresh (${e.message})")
			IdeasSynchronizationData()
		} catch (e: IllegalArgumentException) {
			Napier.w("Corrupt ideas sync.json; starting fresh (${e.message})")
			IdeasSynchronizationData()
		}
	}

	/** Writes the sidecar, creating it if absent — after this, [hasSynced] is true. */
	suspend fun save(data: IdeasSynchronizationData): Unit = withContext(ioDispatcher) {
		val path: Path = getSyncDataPath().toOkioPath()
		fileSystem.write(path) {
			writeUtf8(json.encodeToString(IdeasSynchronizationData.serializer(), data))
		}
	}

	suspend fun update(
		action: (IdeasSynchronizationData) -> IdeasSynchronizationData,
	): IdeasSynchronizationData {
		val updated = action(load())
		save(updated)
		return updated
	}

	/**
	 * Records a delete for propagation on the next sync, dropping the idea's baseline. No-op on
	 * an install that has never synced ideas — deleting the file is genuinely all there is to do.
	 */
	suspend fun recordPendingDelete(id: IdeaId) {
		if (!hasSynced()) return
		update {
			it.copy(
				pendingDeletes = it.pendingDeletes + id,
				baselines = it.baselines - id,
			)
		}
	}

	companion object {
		const val SYNC_FILE_NAME = "sync.json"
	}
}
