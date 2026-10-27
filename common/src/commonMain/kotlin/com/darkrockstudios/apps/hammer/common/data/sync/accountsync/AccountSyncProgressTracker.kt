package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface AccountSyncProgress {
	/** Not started: [SyncAccountUseCase.executeIfIdle] may still skip the run. */
	data object Waiting : AccountSyncProgress

	data object Account : AccountSyncProgress

	data class Projects(val done: Int, val total: Int) : AccountSyncProgress
}

/**
 * Folds [SyncAccountListener] callbacks into the progress of a whole account sync. Safe to call
 * from the parallel project syncs.
 */
class AccountSyncProgressTracker {
	private val _progress = MutableStateFlow<AccountSyncProgress>(AccountSyncProgress.Waiting)
	val progress: StateFlow<AccountSyncProgress> = _progress.asStateFlow()

	/** The use case reports the projects before account sync, then the final list before syncing them. */
	fun onProjectsDiscovered(count: Int) = _progress.update {
		if (it == AccountSyncProgress.Waiting) AccountSyncProgress.Account else AccountSyncProgress.Projects(0, count)
	}

	/** Ignored until the projects start: a failed account sync reports every project failed first. */
	fun onProjectFinished() = _progress.update {
		if (it is AccountSyncProgress.Projects) it.copy(done = it.done + 1) else it
	}
}
