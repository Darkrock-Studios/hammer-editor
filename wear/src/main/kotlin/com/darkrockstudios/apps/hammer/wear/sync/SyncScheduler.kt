package com.darkrockstudios.apps.hammer.wear.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Keeps the periodic background sync scheduled exactly while an account is signed in. */
class SyncScheduler(
	private val workManager: WorkManager,
	private val globalSettingsStore: GlobalSettingsStore,
	private val appScope: CoroutineScope,
) {
	fun start() {
		applySignedIn(globalSettingsStore.serverSettings.isSignedIn())
		appScope.launch {
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
				applySignedIn(settings.isSignedIn())
			}
		}
	}

	fun cancel() {
		workManager.cancelUniqueWork(WORK_NAME)
	}

	private fun applySignedIn(signedIn: Boolean) {
		if (signedIn) schedule() else cancel()
	}

	private fun schedule() {
		val constraints = Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.setRequiresBatteryNotLow(true)
			.build()
		val request = PeriodicWorkRequestBuilder<AccountSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
			.setConstraints(constraints)
			.build()
		workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
	}

	private companion object {
		const val WORK_NAME = "hammer_account_sync"
		const val SYNC_INTERVAL_HOURS = 6L
	}
}
