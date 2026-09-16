package com.darkrockstudios.apps.hammer.wear.sync

import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountResult
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** The account sync the watch runs, behind a seam so the coordinator can be tested without a server. */
fun interface AccountSync {
	suspend fun run(
		listener: SyncAccountListener,
		projectFilter: (SyncedProjectDefinition) -> Boolean,
	): SyncAccountResult
}

/**
 * The one place the watch syncs from. The UI, app-open auto-sync, and background work all go
 * through here so two syncs never overlap, and a sync started from the UI outlives the screen.
 */
interface SyncCoordinator {
	val status: StateFlow<SyncStatus>

	/** Starts a sync in the app scope and returns immediately. */
	fun requestSync(trigger: SyncTrigger)

	/** Starts the app-open sync, at most once per process and only when auto sync applies. */
	fun requestAutoSync()

	suspend fun sync(trigger: SyncTrigger): SyncRunResult

	/** Runs [block] once no sync is in progress, holding off new syncs until it returns. */
	suspend fun <T> runExclusive(block: suspend () -> T): T

	/** Clears the once-per-process latch so the next account signed in still syncs on open. */
	fun resetAutoSync()
}

class DefaultSyncCoordinator(
	private val accountSync: AccountSync,
	private val subscriptions: SubscribedProjectsRepository,
	private val globalSettingsStore: GlobalSettingsStore,
	private val networkConnectivity: NetworkConnectivity,
	private val appScope: CoroutineScope,
) : SyncCoordinator {

	private val _status = MutableStateFlow(SyncStatus())
	override val status: StateFlow<SyncStatus> = _status

	private val mutex = Mutex()
	private val autoSynced = AtomicBoolean(false)

	override fun requestSync(trigger: SyncTrigger) {
		appScope.launch { sync(trigger) }
	}

	override fun requestAutoSync() {
		appScope.launch {
			if (!globalSettingsStore.serverSettings.isSignedIn()) return@launch
			if (!globalSettingsStore.globalSettings.automaticSyncing) return@launch
			if (!networkConnectivity.hasActiveConnection()) return@launch
			if (autoSynced.getAndSet(true)) return@launch
			sync(SyncTrigger.AppOpen)
		}
	}

	override suspend fun sync(trigger: SyncTrigger): SyncRunResult {
		if (!globalSettingsStore.serverSettings.isSignedIn()) return SyncRunResult.Skipped
		if (!mutex.tryLock()) return SyncRunResult.Busy

		try {
			val subscribed = subscriptions.currentSubscriptions()
			_status.value = SyncStatus(running = true, trigger = trigger)

			val result = accountSync.run(listener) { synced -> synced.projectId in subscribed }

			_status.update { it.copy(running = false, lastResult = result) }
			return SyncRunResult.Completed(result)
		} catch (e: CancellationException) {
			_status.update { it.copy(running = false) }
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Watch sync failed", e)
			_status.update { it.copy(running = false, lastRunFailed = true) }
			return SyncRunResult.Failed
		} finally {
			mutex.unlock()
		}
	}

	override suspend fun <T> runExclusive(block: suspend () -> T): T = mutex.withLock { block() }

	override fun resetAutoSync() {
		autoSynced.set(false)
	}

	private val listener = object : SyncAccountListener {
		override suspend fun onLog(message: SyncLogMessage) {
			Napier.i(message.message)
			_status.update { it.copy(log = (it.log + message).takeLast(MAX_LOG_ENTRIES)) }
		}

		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {
			_status.update { status ->
				status.copy(projects = projects.associate { it.name to (status.projects[it.name] ?: ProjectSyncState()) })
			}
		}

		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {
			updateProject(projectDef) { it.copy(progress = progress ?: it.progress, outcome = null) }
		}

		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {
			updateProject(projectDef) { it.copy(outcome = outcome) }
		}

		override suspend fun onUnauthorized() {
			_status.update { it.copy(needsReauth = true) }
		}

		// The watch has no conflict UI; the idea stays unresolved for a phone or desktop to settle.
		override suspend fun onIdeaConflict(conflict: IdeaConflict): StoryIdea? = null
	}

	private fun updateProject(projectDef: ProjectDef, block: (ProjectSyncState) -> ProjectSyncState) {
		_status.update { status ->
			val current = status.projects[projectDef.name] ?: ProjectSyncState()
			status.copy(projects = status.projects + (projectDef.name to block(current)))
		}
	}

	private companion object {
		const val MAX_LOG_ENTRIES = 300
	}
}
