package com.darkrockstudios.apps.hammer.android.sync

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.isSignedIn
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncProgress
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncProgressTracker
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncSetting
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncWindow
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class NightlySyncWorker(
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

	private val nightlySyncSetting: NightlySyncSetting by inject()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val syncAccountUseCase: SyncAccountUseCase by inject()
	private val scheduler: NightlySyncScheduler by inject()
	private val clock: Clock by inject()

	private val notifier = NightlySyncNotifier(context)
	private var foreground = true
	private var postedDirectly = false

	override suspend fun doWork(): Result {
		// The scheduler cancels the chain when either goes false; this guards a run already in flight.
		if (!nightlySyncSetting.enabled.value) return Result.success()
		if (!globalSettingsStore.serverSettings.isSignedIn()) return Result.success()

		// Constraints can hold the run back until morning; leave the sync to the next night then.
		if (!NightlySyncWindow.contains(clock.now(), TimeZone.currentSystemDefault())) {
			Napier.i { "Nightly sync missed its window, waiting for the next night" }
			return finishNight()
		}

		val listener = ProgressListener()
		val result = try {
			coroutineScope {
				val updates = launch { showProgress(listener.tracker.progress) }
				try {
					syncAccountUseCase.executeIfIdle(listener)
				} finally {
					updates.cancel()
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Nightly sync failed", e)
			return Result.retry()
		} finally {
			// A foreground notification goes away with the service; one posted directly does not.
			if (postedDirectly) notifier.cancel()
		}

		return when {
			result == null -> {
				Napier.i { "Nightly sync skipped, an account sync is already running" }
				finishNight()
			}

			result.accountSuccess -> finishNight()
			// Only the user can sign in again, so retrying tonight would just fail again.
			listener.unauthorized -> finishNight()
			else -> Result.retry()
		}
	}

	private fun finishNight(): Result {
		scheduler.scheduleNextNight()
		return Result.success()
	}

	private suspend fun showProgress(progress: StateFlow<AccountSyncProgress>) {
		// Waiting until the use case starts keeps a skipped run from flashing a notification.
		progress.filter { it != AccountSyncProgress.Waiting }.collect {
			show(notifier.build(it))
			// Unchanged projects finish in a burst, and Android drops updates past a few a second.
			delay(UPDATE_INTERVAL)
		}
	}

	private suspend fun show(notification: Notification) {
		if (foreground) {
			try {
				setForeground(foregroundInfo(notification))
				return
			} catch (e: IllegalStateException) {
				// Android refuses a foreground service started from the background unless the app is
				// exempt from battery optimisation. The sync still runs, within the usual job time limit.
				Napier.i { "Nightly sync running without a foreground service: ${e.message}" }
				foreground = false
			}
		}
		notifier.post(notification)
		postedDirectly = true
	}

	private fun foregroundInfo(notification: Notification): ForegroundInfo =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(
				NightlySyncNotifier.NOTIFICATION_ID,
				notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
			)
		} else {
			ForegroundInfo(NightlySyncNotifier.NOTIFICATION_ID, notification)
		}

	private class ProgressListener : SyncAccountListener {
		val tracker = AccountSyncProgressTracker()

		var unauthorized = false
			private set

		override suspend fun onLog(message: SyncLogMessage) {
			val text = message.projectName?.let { "$it - ${message.message}" } ?: message.message
			Napier.log(message.level.toNapierLevel(), TAG, null, text)
		}

		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {
			tracker.onProjectsDiscovered(projects.size)
		}

		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {}

		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {
			Napier.i(tag = TAG) { "${projectDef.name}: $outcome" }
			tracker.onProjectFinished()
		}

		override suspend fun onUnauthorized() {
			unauthorized = true
			Napier.w(tag = TAG) { "Server rejected the session, sign in again to resume nightly sync" }
		}
	}

	private companion object {
		const val TAG = "NightlySync"
		val UPDATE_INTERVAL = 500.milliseconds
	}
}
