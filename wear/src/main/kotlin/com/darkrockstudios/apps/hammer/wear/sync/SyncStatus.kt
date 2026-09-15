package com.darkrockstudios.apps.hammer.wear.sync

import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountResult
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage

enum class SyncTrigger { AppOpen, Manual, Periodic }

/** Per-project progress, keyed by project name like [SyncAccountResult.projects]. */
data class ProjectSyncState(
	val progress: Float? = null,
	val outcome: ProjectSyncOutcome? = null,
)

data class SyncStatus(
	val running: Boolean = false,
	val trigger: SyncTrigger? = null,
	val projects: Map<String, ProjectSyncState> = emptyMap(),
	val log: List<SyncLogMessage> = emptyList(),
	val lastResult: SyncAccountResult? = null,
	val lastRunFailed: Boolean = false,
	val needsReauth: Boolean = false,
)

sealed interface SyncRunResult {
	/** Another sync was already running, or there is no account to sync. */
	data object Skipped : SyncRunResult
	data class Completed(val result: SyncAccountResult) : SyncRunResult
	data object Failed : SyncRunResult
}
