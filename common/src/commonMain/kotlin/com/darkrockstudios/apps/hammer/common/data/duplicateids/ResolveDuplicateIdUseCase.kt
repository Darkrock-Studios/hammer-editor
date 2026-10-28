package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService.ResolveResult
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resolves a duplicate set the user chose, backing the project up first if any copy will be deleted. */
class ResolveDuplicateIdUseCase(
	private val projectDef: ProjectDef,
	private val duplicateIdService: DuplicateIdService,
	private val backupService: ProjectBackupService,
) {
	private val backupLock = Mutex()

	// One backup per open project covers every set resolved after it.
	private var backedUp = false

	suspend operator fun invoke(set: DuplicateIdSet, keep: Set<HPath>): ResolveResult {
		val deletesSomething = set.copies.any { it.path !in keep }
		if (deletesSomething && !ensureBackup()) return ResolveResult.BackupFailed
		return duplicateIdService.resolve(set, keep)
	}

	private suspend fun ensureBackup(): Boolean = backupLock.withLock {
		if (backedUp || !backupService.supportsBackup()) return@withLock true
		backedUp = backupService.createBackup(projectDef) != null
		backedUp
	}
}
