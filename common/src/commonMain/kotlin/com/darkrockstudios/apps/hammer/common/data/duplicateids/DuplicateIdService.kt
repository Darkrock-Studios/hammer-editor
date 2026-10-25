package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
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
	private val backupRepository: ProjectBackupRepository,
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val ioDispatcher by injectIoDispatcher()

	// Lazy: the open step runs before these stores load.
	private val idAllocator: IdAllocator by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val notesRepository: NotesRepository by projectInject()

	private val _unresolved = MutableStateFlow<List<DuplicateIdSet>>(emptyList())
	val unresolved: StateFlow<List<DuplicateIdSet>> = _unresolved

	private val mutex = Mutex()
	private var backedUp = false

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

	/** A fresh scan, so copies a file-sync tool delivered while the project was open count too. */
	suspend fun hasDuplicates(): Boolean = withContext(ioDispatcher) {
		rationalizer.findDuplicates().isNotEmpty()
	}

	suspend fun resolve(set: DuplicateIdSet, keep: Set<HPath>): ResolveResult = withContext(ioDispatcher) {
		mutex.withLock {
			val deletesSomething = set.copies.any { it.path !in keep }
			if (deletesSomething && !ensureBackup()) return@withContext ResolveResult.BackupFailed

			val markDirty = try {
				rationalizer.resolve(set, keep) { idAllocator.claimNextId() }
			} catch (e: StaleDuplicateIdSet) {
				Napier.w("Duplicate set changed before it was resolved", e)
				_unresolved.value = rationalizer.findDuplicates()
				return@withContext ResolveResult.Stale
			}
			markDirty.forEach { syncJournal.markEntityAsDirty(it) }

			_unresolved.value = rationalizer.findDuplicates()
			reload(set.kind)
			ResolveResult.Resolved
		}
	}

	private suspend fun ensureBackup(): Boolean {
		if (backedUp || !backupRepository.supportsBackup()) return true
		backedUp = backupRepository.createBackup(projectDef) != null
		return backedUp
	}

	private suspend fun reload(kind: DuplicateIdKind) {
		when (kind) {
			DuplicateIdKind.Entry, DuplicateIdKind.EntryImage -> encyclopediaRepository.loadEntries()
			DuplicateIdKind.Scene -> sceneRepository.reloadTreeFromDisk()
			DuplicateIdKind.Note -> notesRepository.loadNotes()
			DuplicateIdKind.Draft -> Unit
		}
	}

	enum class ResolveResult {
		Resolved,
		BackupFailed,
		Stale,
	}
}
