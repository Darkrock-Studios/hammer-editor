package com.darkrockstudios.apps.hammer.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.isSignedIn
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncSetting
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncWindow
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

class NightlySyncWorker(
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

	private val nightlySyncSetting: NightlySyncSetting by inject()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val syncAccountUseCase: SyncAccountUseCase by inject()
	private val scheduler: NightlySyncScheduler by inject()
	private val clock: Clock by inject()

	override suspend fun doWork(): Result {
		// The scheduler cancels the chain when either goes false; this guards a run already in flight.
		if (!nightlySyncSetting.enabled.value) return Result.success()
		if (!globalSettingsStore.serverSettings.isSignedIn()) return Result.success()

		// Constraints can hold the run back until morning; leave the sync to the next night then.
		if (!NightlySyncWindow.contains(clock.now(), TimeZone.currentSystemDefault())) {
			Napier.i { "Nightly sync missed its window, waiting for the next night" }
			return finishNight()
		}

		val listener = LoggingListener()
		val result = try {
			syncAccountUseCase.executeIfIdle(listener)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Nightly sync failed", e)
			return Result.retry()
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

	private class LoggingListener : SyncAccountListener {
		var unauthorized = false
			private set

		override suspend fun onLog(message: SyncLogMessage) {
			val text = message.projectName?.let { "$it - ${message.message}" } ?: message.message
			Napier.log(message.level.toNapierLevel(), TAG, null, text)
		}

		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {}

		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {}

		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {
			Napier.i(tag = TAG) { "${projectDef.name}: $outcome" }
		}

		override suspend fun onUnauthorized() {
			unauthorized = true
			Napier.w(tag = TAG) { "Server rejected the session, sign in again to resume nightly sync" }
		}
	}

	private companion object {
		const val TAG = "NightlySync"
	}
}
