package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException

/**
 * Project-open step that settles files sharing an id. Identical copies are resolved before any
 * store loads; the rest wait in [unresolved] for the user, and sync refuses to run until they are
 * gone, so a discarded copy can never be uploaded.
 */
class DuplicateIdService(
	private val projectDef: ProjectDef,
	private val rationalizer: ProjectFileRationalizer,
	private val syncJournal: SyncJournal,
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val ioDispatcher by injectIoDispatcher()

	// Lazy: the open step runs before these stores load.
	private val idAllocator: IdAllocator by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val imagesRepository: ImagesRepository by projectInject()
	private val entitySynchronizers: EntitySynchronizers by projectInject()

	private val _unresolved = MutableStateFlow<List<DuplicateIdSet>>(emptyList())
	val unresolved: StateFlow<List<DuplicateIdSet>> = _unresolved

	private val mutex = Mutex()

	/**
	 * Must run before any store loads its files. A scan that fails (say a sync tool removed a file
	 * mid-scan) must not stop the project opening; sync re-scans before it runs.
	 */
	suspend fun rationalizeOnOpen() = withContext(ioDispatcher) {
		mutex.withLock {
			val resolution = try {
				rationalizer.resolveSafeDuplicates()
			} catch (e: IOException) {
				Napier.e("Duplicate id scan failed for ${projectDef.name}", e)
				return@withContext
			}
			resolution.markDirty.forEach { syncJournal.markEntityAsDirty(it) }
			if (resolution.remaining.isNotEmpty()) {
				Napier.w("${projectDef.name} has ${resolution.remaining.size} unresolved duplicate ids")
			}
			_unresolved.value = resolution.remaining
		}
	}

	/** Gives every extra holder of an id shared across types a fresh id. Needs the stores loaded. */
	suspend fun resolveCrossTypeIds() = withContext(ioDispatcher) {
		mutex.withLock { resolveCrossTypeIdsLocked() }
	}

	private suspend fun resolveCrossTypeIdsLocked() {
		// Re-id acts on whichever file a store reads for an id, so each store must hold one first.
		if (_unresolved.value.isNotEmpty()) return

		val collisions = try {
			rationalizer.findCrossTypeIds()
		} catch (e: IOException) {
			Napier.e("Cross-type id scan failed for ${projectDef.name}", e)
			return
		}
		if (collisions.isEmpty()) return

		// A store that fails here must not stop the project opening; sync stays blocked on what's left.
		for ((id, types) in collisions) {
			attempt("Renumbering id $id shared by $types") {
				types.forEach { entitySynchronizers[it].prepareForSync() }
				val keeper = keeperOf(id, types)
				for (type in types - keeper) {
					val newId = idAllocator.claimNextId()
					Napier.i("$type $id shares its id with $keeper $id; renumbering it to $newId")
					// Rewritten referrers must upload, or the next sync downloads the server copy over them.
					entitySynchronizers[type].reIdEntity(oldId = id, newId = newId)
						.forEach { syncJournal.markEntityAsDirty(it) }
				}
			}
		}
	}

	/** The item the server last confirmed for [id] keeps it; otherwise the most depended-on type. */
	private suspend fun keeperOf(id: Int, types: List<EntityType>): EntityType {
		val syncedHash = if (syncJournal.isServerSynchronized()) syncJournal.loadSyncData().syncedHashes[id] else null
		val serverMatch = syncedHash?.let { hash ->
			types.firstOrNull { type -> attempt("Hashing $type $id") { entitySynchronizers[type].getEntityHash(id) } == hash }
		}
		return serverMatch ?: types.minBy { CROSS_TYPE_PRECEDENCE.indexOf(it) }
	}

	@Suppress("TooGenericExceptionCaught")
	private suspend fun <T> attempt(what: String, block: suspend () -> T): T? =
		try {
			block()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.e("$what failed for ${projectDef.name}", e)
			null
		}

	/** A fresh scan, so copies a file-sync tool delivered while the project was open count too. */
	suspend fun hasDuplicates(): Boolean = withContext(ioDispatcher) {
		rationalizer.hasDuplicates() || rationalizer.findCrossTypeIds().isNotEmpty()
	}

	/** Deletes the copies not in [keep]; [ResolveDuplicateIdUseCase] backs the project up first. */
	suspend fun resolve(set: DuplicateIdSet, keep: Set<HPath>): ResolveResult = withContext(ioDispatcher) {
		mutex.withLock {
			val result = try {
				rationalizer.resolve(set, keep) { idAllocator.claimNextId() }
					.forEach { syncJournal.markEntityAsDirty(it) }
				ResolveResult.Resolved
			} catch (e: StaleDuplicateIdSet) {
				Napier.w("Duplicate set changed before it was resolved", e)
				ResolveResult.Stale
			}

			_unresolved.value = rationalizer.findDuplicates()
			if (result == ResolveResult.Resolved) {
				reload(set.kind)
				resolveCrossTypeIdsLocked()
			}
			result
		}
	}

	private suspend fun reload(kind: DuplicateIdKind) {
		when (kind) {
			DuplicateIdKind.Entry -> encyclopediaRepository.loadEntries()
			DuplicateIdKind.Scene -> sceneRepository.reloadTreeFromDisk()
			DuplicateIdKind.Note -> notesRepository.loadNotes()
			DuplicateIdKind.Draft -> Unit
			DuplicateIdKind.Image -> imagesRepository.loadImages()
		}
	}

	private companion object {
		// Other entities reference images by id. Scenes own drafts, metadata and buffers by id, and
		// scene metadata points at entries.
		val CROSS_TYPE_PRECEDENCE = listOf(
			EntityType.Image,
			EntityType.Scene,
			EntityType.EncyclopediaEntry,
			EntityType.TimelineEvent,
			EntityType.Note,
			EntityType.SceneDraft,
		)
	}

	enum class ResolveResult {
		Resolved,
		BackupFailed,
		Stale,
	}
}
