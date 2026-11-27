package com.darkrockstudios.apps.hammer.wear.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn
import com.darkrockstudios.apps.hammer.wear.tile.CaptureSurfaceUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeps the periodic background sync scheduled exactly while an account is signed in, and redraws
 * the tile and complication when that changes, since they swap between capture and a sign-in prompt.
 */
class SyncScheduler(
	private val workManager: WorkManager,
	private val globalSettingsStore: GlobalSettingsStore,
	private val surfaces: CaptureSurfaceUpdater,
	private val appScope: CoroutineScope,
) {
	fun start() {
		var signedIn = globalSettingsStore.serverSettings.isSignedIn()
		applySignedIn(signedIn)
		appScope.launch {
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
				val now = settings.isSignedIn()
				if (now != signedIn) {
					signedIn = now
					applySignedIn(now)
					surfaces.refresh()
				}
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
