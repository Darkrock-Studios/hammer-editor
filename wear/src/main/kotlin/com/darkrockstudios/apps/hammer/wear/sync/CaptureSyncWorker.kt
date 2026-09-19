package com.darkrockstudios.apps.hammer.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CaptureSyncWorker(
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

	private val coordinator: SyncCoordinator by inject()

	override suspend fun doWork(): Result = when (val run = coordinator.sync(SyncTrigger.Capture)) {
		SyncRunResult.Skipped -> Result.success()
		// The running sync may have passed this project before the capture landed.
		SyncRunResult.Busy -> Result.retry()
		SyncRunResult.Failed -> Result.retry()
		is SyncRunResult.Completed -> when {
			run.result.accountSuccess -> Result.success()
			// Only the user can mint a new session, so retrying just wakes the radio.
			coordinator.status.value.needsReauth -> Result.success()
			else -> Result.retry()
		}
	}
}
