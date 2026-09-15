package com.darkrockstudios.apps.hammer.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AccountSyncWorker(
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

	private val coordinator: SyncCoordinator by inject()
	private val globalSettingsStore: GlobalSettingsStore by inject()

	override suspend fun doWork(): Result {
		if (!globalSettingsStore.globalSettings.automaticSyncing) return Result.success()

		return when (val run = coordinator.sync(SyncTrigger.Periodic)) {
			SyncRunResult.Skipped -> Result.success()
			SyncRunResult.Failed -> Result.retry()
			is SyncRunResult.Completed -> if (run.result.accountSuccess) Result.success() else Result.retry()
		}
	}
}
